#!/usr/bin/env bash

set -euo pipefail

gateway_base_url="${WORTHIT_GATEWAY_BASE_URL:-http://127.0.0.1:18080}"
gateway_base_url="${gateway_base_url%/}"
temporary_dir="$(mktemp -d)"
response_file="${temporary_dir}/response.json"
header_file="${temporary_dir}/headers.txt"
http_status=""
primary_token=""
secondary_token=""
primary_password="${WORTHIT_AUTH_LOCAL_PASSWORD:-}"
secondary_password="${WORTHIT_AUTH_SECONDARY_PASSWORD:-}"
run_suffix="$(date +%s)-$$"
today="$(date +%Y-%m-%d)"
if tomorrow="$(date -v+1d +%Y-%m-%d 2>/dev/null)"; then
  :
else
  tomorrow="$(date -d tomorrow +%Y-%m-%d)"
fi
item_ids=()
subscription_ids=()
wish_ids=()

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

pass() {
  printf 'PASS: %s\n' "$1"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 \
    || fail "required command not found: $1"
}

require_env() {
  [[ -n "${!1:-}" ]] \
    || fail "required environment variable is missing: $1"
}

uuid() {
  uuidgen | tr '[:upper:]' '[:lower:]'
}

token_for_role() {
  case "$1" in
    primary) printf '%s' "${primary_token}" ;;
    secondary) printf '%s' "${secondary_token}" ;;
    *) fail "unknown authentication role: $1" ;;
  esac
}

write_headers() {
  local role="$1"
  local idempotency_key="${2:-}"
  local token
  token="$(token_for_role "${role}")"
  [[ -n "${token}" ]] || fail "${role} token is unavailable"

  : >"${header_file}"
  chmod 600 "${header_file}"
  printf 'Content-Type: application/json\n' >>"${header_file}"
  printf 'Authorization: Bearer %s\n' "${token}" >>"${header_file}"
  if [[ -n "${idempotency_key}" ]]; then
    printf 'Idempotency-Key: %s\n' \
      "${idempotency_key}" >>"${header_file}"
  fi
}

api_request() {
  local role="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local idempotency_key="${5:-}"

  write_headers "${role}" "${idempotency_key}"
  if [[ -n "${body}" ]]; then
    http_status="$(curl --silent --show-error \
      --output "${response_file}" \
      --write-out '%{http_code}' \
      --request "${method}" \
      --header "@${header_file}" \
      --data-binary @- \
      "${gateway_base_url}${path}" <<<"${body}")" \
      || fail "${method} ${path} request failed"
  else
    http_status="$(curl --silent --show-error \
      --output "${response_file}" \
      --write-out '%{http_code}' \
      --request "${method}" \
      --header "@${header_file}" \
      "${gateway_base_url}${path}")" \
      || fail "${method} ${path} request failed"
  fi
}

anonymous_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"

  if [[ -n "${body}" ]]; then
    http_status="$(curl --silent --show-error \
      --output "${response_file}" \
      --write-out '%{http_code}' \
      --request "${method}" \
      --header 'Content-Type: application/json' \
      --data-binary @- \
      "${gateway_base_url}${path}" <<<"${body}")" \
      || fail "${method} ${path} request failed"
  else
    http_status="$(curl --silent --show-error \
      --output "${response_file}" \
      --write-out '%{http_code}' \
      --request "${method}" \
      "${gateway_base_url}${path}")" \
      || fail "${method} ${path} request failed"
  fi
}

fail_api() {
  local description="$1"
  local code
  local trace_id
  code="$(jq -r '.code // "UNKNOWN"' "${response_file}" \
    2>/dev/null || printf 'INVALID_JSON')"
  trace_id="$(jq -r '.traceId // "missing"' "${response_file}" \
    2>/dev/null || printf 'missing')"
  fail "${description}; http=${http_status}, code=${code}, traceId=${trace_id}"
}

assert_success() {
  local description="$1"
  [[ "${http_status}" == "200" ]] || fail_api "${description}"
  jq -e '.success == true and .code == "OK"' \
    "${response_file}" >/dev/null \
    || fail_api "${description} returned an invalid success envelope"
  pass "${description}"
}

assert_error() {
  local expected_http="$1"
  local expected_code="$2"
  local description="$3"
  [[ "${http_status}" == "${expected_http}" ]] \
    || fail_api "${description}"
  jq -e --arg code "${expected_code}" \
    '.success == false and .code == $code and .data == null' \
    "${response_file}" >/dev/null \
    || fail_api "${description} returned an invalid error envelope"
  pass "${description}"
}

login() {
  local username="$1"
  local password="$2"
  local body
  body="$(jq -cn --arg username "${username}" \
    --arg password "${password}" \
    '{username:$username,password:$password}')"
  anonymous_request POST '/api/v1/auth/password/login' "${body}"
  assert_success "password login for release-candidate account"
  jq -e '.data.tokenType == "Bearer" and (.data.token | length > 0)' \
    "${response_file}" >/dev/null \
    || fail_api 'password login did not return a Bearer token'
  jq -er '.data.token' "${response_file}"
}

verify_rejected_headers() {
  anonymous_request GET '/api/v1/auth/me'
  assert_error 401 AUTH_UNAUTHORIZED 'missing Bearer is rejected'

  http_status="$(curl --silent --show-error \
    --output "${response_file}" --write-out '%{http_code}' \
    --header 'Authorization: Basic deliberately-invalid' \
    "${gateway_base_url}/api/v1/auth/me")" \
    || fail 'malformed Authorization probe failed'
  assert_error 401 AUTH_UNAUTHORIZED 'malformed Authorization is rejected'

  http_status="$(curl --silent --show-error \
    --output "${response_file}" --write-out '%{http_code}' \
    --header 'worthit-token: deliberately-forged-public-token' \
    "${gateway_base_url}/api/v1/auth/me")" \
    || fail 'forged internal token probe failed'
  assert_error 401 AUTH_UNAUTHORIZED \
    'external forged internal token is rejected'
}

create_item() {
  local name="$1"
  local price="${2:-1000}"
  local purchase_date="${3:-}"
  local body
  local key
  key="$(uuid)"
  body="$(jq -cn --arg name "${name}" --arg price "${price}" \
    --arg purchaseDate "${purchase_date}" \
    '{name:$name,purchasePrice:$price,expectedYears:"1",residualValue:null,
      purchaseDate:(if $purchaseDate == "" then null else $purchaseDate end),
      warrantyExpireDate:null,warrantyReminderEnabled:false,
      brandModel:null,remark:"release-candidate"}')"
  api_request primary POST '/api/v1/items' "${body}" "${key}"
  assert_success "create item ${name}"
  jq -er '.data.id' "${response_file}"
}

delete_active_resource() {
  local resource="$1"
  local id="$2"
  local role="${3:-primary}"
  local detail_path="/api/v1/${resource}/${id}"
  api_request "${role}" GET "${detail_path}"
  if [[ "${http_status}" != "200" ]]; then
    return 0
  fi
  local version
  version="$(jq -er '.data.version' "${response_file}")" || return 0
  api_request "${role}" DELETE \
    "${detail_path}?version=${version}" '' "$(uuid)"
  return 0
}

cleanup() {
  trap - EXIT
  set +e
  if [[ -n "${primary_token}" ]]; then
    local id
    for id in "${wish_ids[@]:-}"; do
      [[ -n "${id}" ]] && delete_active_resource wishes "${id}"
    done
    for id in "${subscription_ids[@]:-}"; do
      [[ -n "${id}" ]] && delete_active_resource subscriptions "${id}"
    done
    for id in "${item_ids[@]:-}"; do
      [[ -n "${id}" ]] && delete_active_resource items "${id}"
    done
  fi
  primary_token=""
  secondary_token=""
  primary_password=""
  secondary_password=""
  rm -rf "${temporary_dir}"
}

wait_for_reminder() {
  local business_id="$1"
  local attempts=30
  local attempt
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    api_request primary GET '/api/v1/reminders?tab=PENDING&page=1&size=50'
    if [[ "${http_status}" == "200" ]] && jq -e \
      --arg businessId "${business_id}" \
      '.data.items | any(.businessId == $businessId and .status == "PENDING")' \
      "${response_file}" >/dev/null; then
      jq -er --arg businessId "${business_id}" \
        '.data.items[] | select(.businessId == $businessId and .status == "PENDING") | .id' \
        "${response_file}" | head -n 1
      return 0
    fi
    sleep 1
  done
  fail "Reminder did not converge for businessId=${business_id}"
}

materialize_due_reminder_fixture() {
  local business_id="$1"
  local instance_id=""
  local attempt
  [[ "${business_id}" =~ ^[1-9][0-9]*$ ]] \
    || fail 'Reminder fixture businessId must be a positive integer'
  for ((attempt = 1; attempt <= 30; attempt++)); do
    instance_id="$(MYSQL_PWD="${WORTHIT_REMINDER_DB_PASSWORD}" mysql \
      --protocol=TCP \
      --host="${WORTHIT_MYSQL_HOST:-127.0.0.1}" \
      --port="${WORTHIT_MYSQL_PORT:-3306}" \
      --user="${WORTHIT_REMINDER_DB_USERNAME}" \
      --batch --skip-column-names \
      worthit_reminder \
      --execute="
        SELECT i.id
        FROM rem_instance i
        JOIN rem_binding b ON b.id = i.binding_id
        WHERE b.business_id = ${business_id}
          AND i.status = 'PENDING'
        ORDER BY i.id DESC
        LIMIT 1;
      " 2>/dev/null)" || fail 'Reminder fixture lookup failed'
    if [[ "${instance_id}" =~ ^[1-9][0-9]*$ ]]; then
      break
    fi
    sleep 1
  done
  [[ "${instance_id}" =~ ^[1-9][0-9]*$ ]] \
    || fail "Reminder Outbox did not converge for businessId=${business_id}"

  local affected
  affected="$(MYSQL_PWD="${WORTHIT_REMINDER_DB_PASSWORD}" mysql \
    --protocol=TCP \
    --host="${WORTHIT_MYSQL_HOST:-127.0.0.1}" \
    --port="${WORTHIT_MYSQL_PORT:-3306}" \
    --user="${WORTHIT_REMINDER_DB_USERNAME}" \
    --batch --skip-column-names \
    worthit_reminder \
    --execute="
      UPDATE rem_instance
      SET remind_at = DATE_SUB(NOW(3), INTERVAL 1 SECOND),
          update_time = NOW(3)
      WHERE id = ${instance_id}
        AND status = 'PENDING';
      SELECT ROW_COUNT();
    " 2>/dev/null)" || fail 'Reminder fixture update failed'
  [[ "${affected}" == "1" ]] \
    || fail 'Reminder fixture did not update exactly one PENDING instance'
  pass 'materialize due Reminder precondition with an exact local fixture'
}

verify_item_and_restore() {
  local name="RC-物品-${run_suffix}"
  local body
  local key
  local item_id
  local version
  local restore_token
  local restore_body
  key="$(uuid)"
  body="$(jq -cn --arg name "${name}" \
    '{name:$name,purchasePrice:"1000",expectedYears:"1",residualValue:null,
      purchaseDate:null,warrantyExpireDate:null,warrantyReminderEnabled:false,
      brandModel:null,remark:"release-candidate"}')"

  api_request primary POST '/api/v1/items' "${body}" "${key}"
  assert_success 'create G-PLAN-01 item'
  jq -e '.data.planDailyCostDisplay == "¥2.74/天"
    and .data.residualUnset == true and (.data.id | type == "string")' \
    "${response_file}" >/dev/null \
    || fail_api 'G-PLAN-01 item response is inconsistent'
  item_id="$(jq -er '.data.id' "${response_file}")"
  item_ids+=("${item_id}")

  api_request primary POST '/api/v1/items' "${body}" "${key}"
  assert_success 'replay item create with the same key and body'
  jq -e --arg id "${item_id}" '.data.id == $id' \
    "${response_file}" >/dev/null \
    || fail_api 'item create replay returned a different id'

  api_request primary POST '/api/v1/items' \
    "$(jq -c '.name += "-conflict"' <<<"${body}")" "${key}"
  assert_error 409 IDEM_CONFLICT \
    'same item key with a different body is rejected'

  api_request secondary GET "/api/v1/items/${item_id}"
  assert_error 404 RES_NOT_FOUND 'cross-user item detail is hidden'

  api_request primary GET "/api/v1/items?keyword=${run_suffix}&page=1&size=20"
  assert_success 'search item through Gateway'
  jq -e --arg id "${item_id}" '.data.items | any(.id == $id)' \
    "${response_file}" >/dev/null \
    || fail_api 'created item is absent from keyword search'

  api_request primary GET "/api/v1/items/${item_id}"
  assert_success 'read item detail before delete'
  version="$(jq -er '.data.version' "${response_file}")"
  api_request primary DELETE "/api/v1/items/${item_id}?version=${version}" \
    '' "$(uuid)"
  assert_success 'delete item and receive restore credential'
  version="$((version + 1))"
  restore_token="$(jq -er '.data.restoreToken' "${response_file}")"
  restore_body="$(jq -cn --argjson version "${version}" \
    --arg restoreToken "${restore_token}" \
    '{version:$version,restoreToken:$restoreToken}')"

  api_request secondary POST "/api/v1/items/${item_id}/restore" \
    "${restore_body}"
  assert_error 404 RES_NOT_FOUND 'cross-user item restore is hidden'

  api_request primary POST "/api/v1/items/${item_id}/restore" \
    "${restore_body}"
  assert_success 'restore item within the restore window'
  api_request primary POST "/api/v1/items/${item_id}/restore" \
    "${restore_body}"
  assert_success 'repeat item restore idempotently'

  api_request primary GET '/api/v1/dashboard'
  assert_success 'read Dashboard after item restore'
  jq -e '.data.itemPlanDailyTotal == "2.74"
    and .data.itemPlanDailyTotalDisplay == "¥2.74/天"
    and .data.itemResidualUnsetCount == 1' \
    "${response_file}" >/dev/null \
    || fail_api 'Dashboard item summary is inconsistent'
}

verify_subscription() {
  local name="RC-订阅-${run_suffix}"
  local body
  local subscription_id
  local version
  local delete_version
  local restore_token
  local restore_body
  body="$(jq -cn --arg name "${name}" \
    '{name:$name,amount:"20",currency:"USD",billingCycleType:"MONTHLY",
      billingCycleValue:null,cnyReferenceAmount:"140",nextRenewalDate:null,
      autoRenew:"UNKNOWN",renewalReminderEnabled:false,remark:"release-candidate"}')"
  api_request primary POST '/api/v1/subscriptions' "${body}" "$(uuid)"
  assert_success 'create USD subscription with CNY reference'
  jq -e '.data.originalMonthlyCostDisplay == "20.00 USD/月"
    and .data.cnyMonthlyCostDisplay == "约 ¥140.00/月"
    and .data.includeInCnyTotal == true' "${response_file}" >/dev/null \
    || fail_api 'subscription cost response is inconsistent'
  subscription_id="$(jq -er '.data.id' "${response_file}")"
  subscription_ids+=("${subscription_id}")
  version="$(jq -er '.data.version' "${response_file}")"

  local pause_key
  local pause_body
  pause_key="$(uuid)"
  pause_body="$(jq -cn --argjson version "${version}" \
    '{version:$version}')"
  api_request primary GET '/api/v1/dashboard'
  assert_success 'read Dashboard with active subscription'
  jq -e '.data.subscriptionMonthlyCnyTotal == "140.00"
    and .data.subscriptionMonthlyCnyTotalDisplay == "约 ¥140.00/月"
    and .data.subscriptionMonthlyCnyApproximate == true' \
    "${response_file}" >/dev/null \
    || fail_api 'active subscription is absent from Dashboard'
  api_request primary POST "/api/v1/subscriptions/${subscription_id}/pause" \
    "${pause_body}" "${pause_key}"
  assert_success 'pause subscription'
  jq -e '.data.status == "PAUSED"' "${response_file}" >/dev/null \
    || fail_api 'subscription did not enter PAUSED'
  version="$(jq -er '.data.version' "${response_file}")"
  api_request primary POST "/api/v1/subscriptions/${subscription_id}/pause" \
    "${pause_body}" "${pause_key}"
  assert_success 'replay pause subscription idempotently'
  api_request primary GET '/api/v1/dashboard'
  assert_success 'read Dashboard with paused subscription'
  jq -e '.data.subscriptionMonthlyCnyTotal == "0.00"' \
    "${response_file}" >/dev/null \
    || fail_api 'paused subscription remains in Dashboard total'

  api_request primary POST "/api/v1/subscriptions/${subscription_id}/resume" \
    "$(jq -cn --argjson version "${version}" \
      '{version:$version,nextRenewalDate:null,renewalReminderEnabled:false}')" \
    "$(uuid)"
  assert_success 'resume subscription with reminders disabled'
  jq -e '.data.status == "ACTIVE"' "${response_file}" >/dev/null \
    || fail_api 'subscription did not return to ACTIVE'
  version="$(jq -er '.data.version' "${response_file}")"
  api_request primary GET '/api/v1/dashboard'
  assert_success 'read Dashboard with resumed subscription'
  jq -e '.data.subscriptionMonthlyCnyTotal == "140.00"' \
    "${response_file}" >/dev/null \
    || fail_api 'resumed subscription is absent from Dashboard total'

  api_request primary POST "/api/v1/subscriptions/${subscription_id}/end" \
    "$(jq -cn --argjson version "${version}" '{version:$version}')" \
    "$(uuid)"
  assert_success 'end subscription'
  jq -e '.data.status == "ENDED"' "${response_file}" >/dev/null \
    || fail_api 'subscription did not enter ENDED'
  delete_version="$(jq -er '.data.version' "${response_file}")"
  api_request primary GET '/api/v1/dashboard'
  assert_success 'read Dashboard with ended subscription'
  jq -e '.data.subscriptionMonthlyCnyTotal == "0.00"' \
    "${response_file}" >/dev/null \
    || fail_api 'ended subscription remains in Dashboard total'

  api_request primary DELETE \
    "/api/v1/subscriptions/${subscription_id}?version=${delete_version}" \
    '' "$(uuid)"
  assert_success 'delete subscription and receive restore credential'
  restore_token="$(jq -er '.data.restoreToken' "${response_file}")"
  restore_body="$(jq -cn --argjson version "$((delete_version + 1))" \
    --arg restoreToken "${restore_token}" \
    '{version:$version,restoreToken:$restoreToken}')"
  api_request secondary POST \
    "/api/v1/subscriptions/${subscription_id}/restore" "${restore_body}"
  assert_error 404 RES_NOT_FOUND 'cross-user subscription restore is hidden'
  api_request primary POST \
    "/api/v1/subscriptions/${subscription_id}/restore" "${restore_body}"
  assert_success 'restore subscription within the restore window'
  api_request primary POST \
    "/api/v1/subscriptions/${subscription_id}/restore" "${restore_body}"
  assert_success 'repeat subscription restore idempotently'
}

verify_wish_reminder_and_purchase() {
  local name="RC-想买-${run_suffix}"
  local body
  local wish_id
  local version
  local reminder_id
  body="$(jq -cn --arg name "${name}" --arg deadline "${tomorrow}" \
    '{name:$name,expectedPrice:"1000",expectedYears:"1",residualValue:null,
      reason:"release-candidate",remark:null,watchDeadline:$deadline,
      watchReminderEnabled:true}')"
  api_request primary POST '/api/v1/wishes' "${body}" "$(uuid)"
  assert_success 'create considering wish with due reminder'
  jq -e '.data.status == "CONSIDERING"
    and .data.planDailyCostDisplay == "¥2.74/天"' \
    "${response_file}" >/dev/null \
    || fail_api 'wish response is inconsistent'
  wish_id="$(jq -er '.data.id' "${response_file}")"
  wish_ids+=("${wish_id}")
  version="$(jq -er '.data.version' "${response_file}")"

  materialize_due_reminder_fixture "${wish_id}"
  reminder_id="$(wait_for_reminder "${wish_id}")"
  api_request primary GET '/api/v1/reminders/pending-count'
  assert_success 'read pending reminder count before ignore'
  local pending_before
  pending_before="$(jq -er '.data.count' "${response_file}")"
  (( pending_before >= 1 )) \
    || fail_api 'pending reminder count did not include the due reminder'
  api_request primary POST "/api/v1/reminders/${reminder_id}/ignore"
  assert_success 'ignore due wish reminder'
  api_request primary GET '/api/v1/reminders/pending-count'
  assert_success 'read pending reminder count after ignore'
  local pending_after
  pending_after="$(jq -er '.data.count' "${response_file}")"
  (( pending_after == pending_before - 1 )) \
    || fail_api 'pending reminder count did not decrease after ignore'
  api_request primary GET '/api/v1/reminders?tab=DONE&page=1&size=50'
  assert_success 'list completed reminders'
  jq -e --arg id "${reminder_id}" \
    '.data.items | any(.id == $id and .status == "IGNORED")' \
    "${response_file}" >/dev/null \
    || fail_api 'ignored reminder is absent from DONE list'

  api_request primary POST "/api/v1/wishes/${wish_id}/abandon" \
    "$(jq -cn --argjson version "${version}" \
      '{version:$version,reason:"release-candidate-abandon"}')" \
    "$(uuid)"
  assert_success 'abandon wish'
  version="$(jq -er '.data.version' "${response_file}")"
  api_request primary POST "/api/v1/wishes/${wish_id}/reconsider" \
    "$(jq -cn --argjson version "${version}" '{version:$version}')" \
    "$(uuid)"
  assert_success 'reconsider wish'
  jq -e '.data.status == "CONSIDERING"
    and .data.lastAbandonReason == "release-candidate-abandon"' \
    "${response_file}" >/dev/null \
    || fail_api 'wish reconsider response lost the latest abandon fact'
  version="$(jq -er '.data.version' "${response_file}")"

  local body_file="${temporary_dir}/purchase-body.json"
  local headers_one="${temporary_dir}/purchase-one.headers"
  local headers_two="${temporary_dir}/purchase-two.headers"
  local response_one="${temporary_dir}/purchase-one.json"
  local response_two="${temporary_dir}/purchase-two.json"
  local status_one_file="${temporary_dir}/purchase-one.status"
  local status_two_file="${temporary_dir}/purchase-two.status"
  jq -cn --argjson version "${version}" '{version:$version}' >"${body_file}"
  chmod 600 "${body_file}"
  for spec in "${headers_one}:$(uuid)" "${headers_two}:$(uuid)"; do
    local file="${spec%%:*}"
    local key="${spec#*:}"
    : >"${file}"
    chmod 600 "${file}"
    printf 'Content-Type: application/json\n' >>"${file}"
    printf 'Authorization: Bearer %s\n' "${primary_token}" >>"${file}"
    printf 'Idempotency-Key: %s\n' "${key}" >>"${file}"
  done
  curl --silent --show-error --output "${response_one}" \
    --write-out '%{http_code}' --request POST --header "@${headers_one}" \
    --data-binary "@${body_file}" \
    "${gateway_base_url}/api/v1/wishes/${wish_id}/purchase" \
    >"${status_one_file}" &
  local pid_one=$!
  curl --silent --show-error --output "${response_two}" \
    --write-out '%{http_code}' --request POST --header "@${headers_two}" \
    --data-binary "@${body_file}" \
    "${gateway_base_url}/api/v1/wishes/${wish_id}/purchase" \
    >"${status_two_file}" &
  local pid_two=$!
  wait "${pid_one}" || fail 'first concurrent wish purchase failed'
  wait "${pid_two}" || fail 'second concurrent wish purchase failed'
  [[ "$(<"${status_one_file}")" == "200" \
      && "$(<"${status_two_file}")" == "200" ]] \
    || fail 'concurrent wish purchases did not both return HTTP 200'
  local item_one
  local item_two
  item_one="$(jq -er '.data.item.id' "${response_one}")"
  item_two="$(jq -er '.data.item.id' "${response_two}")"
  [[ "${item_one}" == "${item_two}" ]] \
    || fail 'concurrent wish purchases returned different item ids'
  item_ids+=("${item_one}")
  pass 'concurrent wish purchase converges to one item'

  api_request primary GET '/api/v1/dashboard'
  assert_success 'read Dashboard after wish purchase'
  jq -e '(.data.itemPlanDailyTotal | type == "string")
    and .data.wishConsideringCount == 0
    and .data.wishConsideringAmountTotal == "0.00"' \
    "${response_file}" >/dev/null \
    || fail_api 'Dashboard did not exclude the purchased wish'

  api_request primary GET "/api/v1/wishes/${wish_id}"
  assert_success 'read purchased wish before delete'
  version="$(jq -er '.data.version' "${response_file}")"
  api_request primary DELETE "/api/v1/wishes/${wish_id}?version=${version}" \
    '' "$(uuid)"
  assert_success 'delete purchased wish and receive restore credential'
  local restore_token
  local restore_body
  restore_token="$(jq -er '.data.restoreToken' "${response_file}")"
  restore_body="$(jq -cn --argjson version "$((version + 1))" \
    --arg restoreToken "${restore_token}" \
    '{version:$version,restoreToken:$restoreToken}')"
  api_request secondary POST "/api/v1/wishes/${wish_id}/restore" \
    "${restore_body}"
  assert_error 404 RES_NOT_FOUND 'cross-user wish restore is hidden'
  api_request primary POST "/api/v1/wishes/${wish_id}/restore" \
    "${restore_body}"
  assert_success 'restore purchased wish within the restore window'
  api_request primary POST "/api/v1/wishes/${wish_id}/restore" \
    "${restore_body}"
  assert_success 'repeat wish restore idempotently'
}

verify_clean_dashboard() {
  api_request primary GET '/api/v1/dashboard'
  assert_success 'verify dedicated release-candidate account is clean'
  jq -e '.data.itemPlanDailyTotal == "0.00"
    and .data.itemResidualUnsetCount == 0
    and .data.subscriptionMonthlyCnyTotal == "0.00"
    and .data.subscriptionUnconvertedForeignCount == 0
    and .data.wishConsideringCount == 0
    and .data.wishConsideringAmountTotal == "0.00"' \
    "${response_file}" >/dev/null \
    || fail_api 'release-candidate account contains pre-existing active data'
}

verify_lifecycle() {
  local old_id
  local new_id
  local sell_id
  local scrap_id
  old_id="$(create_item "RC-旧物品-${run_suffix}" 1000 "${today}" | tail -n 1)"
  item_ids+=("${old_id}")
  new_id="$(create_item "RC-新物品-${run_suffix}" 1200 "${today}" | tail -n 1)"
  item_ids+=("${new_id}")
  sell_id="$(create_item "RC-卖出-${run_suffix}" 1000 "${today}" | tail -n 1)"
  item_ids+=("${sell_id}")
  scrap_id="$(create_item "RC-报废-${run_suffix}" 300 "${today}" | tail -n 1)"
  item_ids+=("${scrap_id}")

  local replace_key
  local relation_id
  replace_key="$(uuid)"
  api_request primary POST "/api/v1/items/${old_id}/replace" \
    "$(jq -cn --arg id "${new_id}" '{newItemId:$id}')" "${replace_key}"
  assert_success 'create item replacement relation'
  relation_id="$(jq -er '.data.relationId' "${response_file}")"
  api_request primary POST "/api/v1/items/${old_id}/replace" \
    "$(jq -cn --arg id "${new_id}" '{newItemId:$id}')" "${replace_key}"
  assert_success 'replay item replacement relation'
  jq -e --arg id "${relation_id}" '.data.relationId == $id' \
    "${response_file}" >/dev/null \
    || fail_api 'replacement replay returned a different relation id'

  api_request primary GET "/api/v1/items/${old_id}"
  local old_version
  old_version="$(jq -er '.data.version' "${response_file}")"
  api_request primary POST "/api/v1/items/${old_id}/return" \
    "$(jq -cn --argjson version "${old_version}" --arg date "${today}" \
      '{version:$version,returnDate:$date,remark:"release-candidate"}')" \
    "$(uuid)"
  assert_success 'return holding item'
  jq -e '.data.lifecycleStatus == "RETURNED"' \
    "${response_file}" >/dev/null || fail_api 'item did not enter RETURNED'

  api_request primary GET "/api/v1/items/${sell_id}"
  local sell_version
  sell_version="$(jq -er '.data.version' "${response_file}")"
  local sell_body
  local sell_key
  sell_body="$(jq -cn --argjson version "${sell_version}" \
    --arg date "${today}" \
    '{version:$version,saleDate:$date,saleAmount:"800",remark:null}')"
  sell_key="$(uuid)"
  api_request primary POST "/api/v1/items/${sell_id}/sell" \
    "${sell_body}" "${sell_key}"
  assert_success 'sell holding item'
  jq -e '.data.lifecycleStatus == "SOLD"
    and .data.disposal.saleAmount == "800.000000"
    and .data.disposal.netCost == "200.000000"' \
    "${response_file}" >/dev/null \
    || fail_api 'sold item snapshot response is inconsistent'
  api_request primary POST "/api/v1/items/${sell_id}/sell" \
    "${sell_body}" "${sell_key}"
  assert_success 'replay sold item command idempotently'
  api_request primary POST "/api/v1/items/${sell_id}/sell" \
    "${sell_body}" "$(uuid)"
  assert_error 409 VAL_STATE_CONFLICT \
    'new disposal command against terminal item is rejected'

  api_request primary GET "/api/v1/items/${scrap_id}"
  local scrap_version
  scrap_version="$(jq -er '.data.version' "${response_file}")"
  api_request primary POST "/api/v1/items/${scrap_id}/scrap" \
    "$(jq -cn --argjson version "${scrap_version}" --arg date "${today}" \
      '{version:$version,scrapDate:$date,remark:null}')" "$(uuid)"
  assert_success 'scrap holding item'
  jq -e '.data.lifecycleStatus == "SCRAPPED"' \
    "${response_file}" >/dev/null || fail_api 'item did not enter SCRAPPED'

  api_request secondary POST "/api/v1/items/${new_id}/scrap" \
    "$(jq -cn --arg date "${today}" \
      '{version:1,scrapDate:$date,remark:null}')" "$(uuid)"
  assert_error 404 RES_NOT_FOUND 'cross-user disposal is hidden'

  api_request primary GET '/api/v1/lifecycle/review?page=1&size=50'
  assert_success 'read lifecycle review'
  jq -e --arg relationId "${relation_id}" --arg sellId "${sell_id}" \
    '(.data.items | any(.id == $relationId and .entryType == "REPLACEMENT"
      and .replacement != null and .disposal == null))
     and (.data.items | any(.entryType == "DISPOSAL"
      and .disposal.item.id == $sellId and .disposal != null
      and .replacement == null))' "${response_file}" >/dev/null \
    || fail_api 'lifecycle review is missing the explicit union branches'
}

main() {
  trap cleanup EXIT
  require_command curl
  require_command jq
  require_command mysql
  require_command uuidgen
  require_env WORTHIT_AUTH_LOCAL_USERNAME
  require_env WORTHIT_AUTH_LOCAL_PASSWORD
  require_env WORTHIT_AUTH_SECONDARY_USERNAME
  require_env WORTHIT_AUTH_SECONDARY_PASSWORD
  require_env WORTHIT_REMINDER_DB_USERNAME
  require_env WORTHIT_REMINDER_DB_PASSWORD

  verify_rejected_headers
  primary_token="$(login "${WORTHIT_AUTH_LOCAL_USERNAME}" \
    "${primary_password}" | tail -n 1)"
  secondary_token="$(login "${WORTHIT_AUTH_SECONDARY_USERNAME}" \
    "${secondary_password}" | tail -n 1)"

  api_request primary GET '/api/v1/auth/me'
  assert_success 'primary Bearer-only current-user request'
  api_request secondary GET '/api/v1/auth/me'
  assert_success 'secondary Bearer-only current-user request'

  verify_clean_dashboard
  verify_item_and_restore
  verify_subscription
  verify_wish_reminder_and_purchase
  verify_lifecycle

  printf 'PASS: M1/M2 release-candidate public API verification completed\n'
}

main "$@"
