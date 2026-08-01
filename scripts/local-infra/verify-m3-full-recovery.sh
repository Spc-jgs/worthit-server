#!/usr/bin/env bash

set -euo pipefail

gateway_base_url="${WORTHIT_GATEWAY_BASE_URL:-http://127.0.0.1:18080}"
gateway_base_url="${gateway_base_url%/}"
wait_seconds="${WORTHIT_RECOVERY_WAIT_SECONDS:-61}"
temporary_dir="$(mktemp -d)"
response_file="${temporary_dir}/response.json"
header_file="${temporary_dir}/headers.txt"
primary_token=""
secondary_token=""
http_status=""
run_suffix="$(date +%s)-$$"
category_id=""
category_deleted=false
item_id=""
subscription_id=""
wish_id=""

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

pass() {
  printf 'PASS: %s\n' "$1"
}

uuid() {
  uuidgen | tr '[:upper:]' '[:lower:]'
}

require_command() {
  command -v "$1" >/dev/null 2>&1 \
    || fail "required command not found: $1"
}

require_env() {
  [[ -n "${!1:-}" ]] \
    || fail "required environment variable is missing: $1"
}

token_for_role() {
  case "$1" in
    primary) printf '%s' "${primary_token}" ;;
    secondary) printf '%s' "${secondary_token}" ;;
    *) fail "unknown authentication role: $1" ;;
  esac
}

api_request() {
  local role="$1"
  local method="$2"
  local path="$3"
  local body="${4:-}"
  local idempotency_key="${5:-}"
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
  if [[ -n "${body}" ]]; then
    http_status="$(curl --silent --show-error \
      --output "${response_file}" --write-out '%{http_code}' \
      --request "${method}" --header "@${header_file}" \
      --data-binary @- "${gateway_base_url}${path}" \
      <<<"${body}")" || fail "${method} ${path} request failed"
  else
    http_status="$(curl --silent --show-error \
      --output "${response_file}" --write-out '%{http_code}' \
      --request "${method}" --header "@${header_file}" \
      "${gateway_base_url}${path}")" \
      || fail "${method} ${path} request failed"
  fi
}

anonymous_request() {
  local body="$1"
  http_status="$(curl --silent --show-error \
    --output "${response_file}" --write-out '%{http_code}' \
    --request POST --header 'Content-Type: application/json' \
    --data-binary @- \
    "${gateway_base_url}/api/v1/auth/password/login" \
    <<<"${body}")" || fail 'password login request failed'
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
    || fail_api "${description} returned invalid success envelope"
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
    || fail_api "${description} returned invalid error envelope"
  pass "${description}"
}

login() {
  local role="$1"
  local username="$2"
  local password="$3"
  anonymous_request "$(jq -cn \
    --arg username "${username}" \
    --arg password "${password}" \
    '{username:$username,password:$password}')"
  assert_success "password login for ${role} recovery account"
  local value
  value="$(jq -er '.data.token' "${response_file}")" \
    || fail "${role} login response has no token"
  case "${role}" in
    primary) primary_token="${value}" ;;
    secondary) secondary_token="${value}" ;;
  esac
}

cleanup() {
  rm -rf "${temporary_dir}"
}

create_resources() {
  local category_name="M3恢复-${run_suffix}"
  api_request primary POST '/api/v1/categories' \
    "$(jq -cn --arg name "${category_name}" '{name:$name}')"
  assert_success 'create M3 recovery category'
  category_id="$(jq -er '.data.id' "${response_file}")"

  api_request primary POST '/api/v1/items' \
    "$(jq -cn --arg name "M3物品-${run_suffix}" \
      --arg categoryId "${category_id}" \
      '{name:$name,categoryId:$categoryId,purchasePrice:"1000",
        expectedYears:"2",residualValue:null,purchaseDate:null,
        warrantyExpireDate:null,warrantyReminderEnabled:false,
        brandModel:null,remark:"m3-full-recovery"}')" "$(uuid)"
  assert_success 'create M3 recovery item'
  item_id="$(jq -er '.data.id' "${response_file}")"
  local item_version
  item_version="$(jq -er '.data.version' "${response_file}")"
  api_request primary DELETE \
    "/api/v1/items/${item_id}?version=${item_version}" '' "$(uuid)"
  assert_success 'delete M3 recovery item'

  api_request primary POST '/api/v1/subscriptions' \
    "$(jq -cn --arg name "M3订阅-${run_suffix}" \
      --arg categoryId "${category_id}" \
      '{name:$name,categoryId:$categoryId,amount:"20",currency:"CNY",
        billingCycleType:"MONTHLY",billingCycleValue:null,
        cnyReferenceAmount:null,nextRenewalDate:null,autoRenew:"UNKNOWN",
        renewalReminderEnabled:false,remark:"m3-full-recovery"}')" "$(uuid)"
  assert_success 'create M3 recovery subscription'
  subscription_id="$(jq -er '.data.id' "${response_file}")"
  local subscription_version
  subscription_version="$(jq -er '.data.version' "${response_file}")"
  api_request primary DELETE \
    "/api/v1/subscriptions/${subscription_id}?version=${subscription_version}" \
    '' "$(uuid)"
  assert_success 'delete M3 recovery subscription'

  api_request primary POST '/api/v1/wishes' \
    "$(jq -cn --arg name "M3想买-${run_suffix}" \
      --arg categoryId "${category_id}" \
      '{name:$name,categoryId:$categoryId,expectedPrice:"500",
        expectedYears:"1",residualValue:null,reason:"m3-full-recovery",
        remark:null,watchDeadline:null,watchReminderEnabled:false}')" "$(uuid)"
  assert_success 'create M3 recovery wish'
  wish_id="$(jq -er '.data.id' "${response_file}")"
  local wish_version
  wish_version="$(jq -er '.data.version' "${response_file}")"
  api_request primary DELETE \
    "/api/v1/wishes/${wish_id}?version=${wish_version}" '' "$(uuid)"
  assert_success 'delete M3 recovery wish'
}

verify_deleted_list_and_isolation() {
  api_request primary GET '/api/v1/recovery/resources?page=1&size=50'
  assert_success 'list all deleted M3 resources'
  for spec in "${item_id}:ITEM" \
      "${subscription_id}:SUBSCRIPTION" "${wish_id}:WISH"; do
    local id="${spec%%:*}"
    local type="${spec#*:}"
    jq -e --arg id "${id}" --arg type "${type}" \
      '.data.items | any(.id == $id and .resourceType == $type
        and (.version | type == "number"))' \
      "${response_file}" >/dev/null \
      || fail_api "deleted list is missing ${type} ${id}"
  done

  api_request primary GET \
    '/api/v1/recovery/resources?resourceType=ITEM&page=1&size=50'
  assert_success 'filter deleted resources by ITEM'
  jq -e --arg id "${item_id}" \
    '.data.items | length == 1 and .[0].id == $id
      and .[0].resourceType == "ITEM"' \
    "${response_file}" >/dev/null \
    || fail_api 'ITEM recovery filter is inconsistent'

  api_request secondary GET '/api/v1/recovery/resources?page=1&size=50'
  assert_success 'list deleted resources as secondary user'
  jq -e --arg item "${item_id}" --arg subscription "${subscription_id}" \
    --arg wish "${wish_id}" \
    '.data.items | all(.id != $item and .id != $subscription and .id != $wish)' \
    "${response_file}" >/dev/null \
    || fail_api 'secondary user can see primary deleted resources'
}

version_for_deleted() {
  local id="$1"
  api_request primary GET '/api/v1/recovery/resources?page=1&size=50'
  assert_success "read deleted version for ${id}"
  jq -er --arg id "${id}" '.data.items[] | select(.id == $id) | .version' \
    "${response_file}"
}

restore_resource() {
  local type="$1"
  local id="$2"
  local version="$3"
  local key="$4"
  api_request primary POST \
    "/api/v1/recovery/resources/${type}/${id}/restore" \
    "$(jq -cn --argjson version "${version}" '{version:$version}')" \
    "${key}"
  assert_success "full restore ${type} after short window"
  jq -e --arg id "${id}" --arg type "${type}" \
    '.data.id == $id and .data.resourceType == $type
      and .data.categoryFallbackApplied == true
      and .data.categoryName == "未分类"' \
    "${response_file}" >/dev/null \
    || fail_api "${type} full restore response is inconsistent"
}

verify_long_restore_and_cleanup() {
  printf 'INFO: waiting %s seconds for the short restore window to expire\n' \
    "${wait_seconds}"
  sleep "${wait_seconds}"

  api_request primary DELETE "/api/v1/categories/${category_id}"
  assert_success 'delete original category after short restore window'
  category_deleted=true

  local item_version
  local subscription_version
  local wish_version
  item_version="$(version_for_deleted "${item_id}" | tail -n 1)"
  subscription_version="$(version_for_deleted "${subscription_id}" | tail -n 1)"
  wish_version="$(version_for_deleted "${wish_id}" | tail -n 1)"

  api_request secondary POST \
    "/api/v1/recovery/resources/ITEM/${item_id}/restore" \
    "$(jq -cn --argjson version "${item_version}" '{version:$version}')" \
    "$(uuid)"
  assert_error 404 RES_NOT_FOUND 'cross-user full restore is hidden'

  local item_key
  item_key="$(uuid)"
  restore_resource ITEM "${item_id}" "${item_version}" "${item_key}"
  local item_restored_version
  item_restored_version="$(jq -er '.data.version' "${response_file}")"
  restore_resource ITEM "${item_id}" "${item_version}" "${item_key}"
  jq -e --argjson version "${item_restored_version}" \
    '.data.version == $version' "${response_file}" >/dev/null \
    || fail_api 'same-key full restore replay changed the response'

  api_request primary POST \
    "/api/v1/recovery/resources/ITEM/${item_id}/restore" \
    "$(jq -cn --argjson version "${item_version}" '{version:$version}')" \
    "$(uuid)"
  assert_error 409 VAL_STATE_CONFLICT \
    'new-key full restore with stale version is rejected'

  restore_resource SUBSCRIPTION "${subscription_id}" \
    "${subscription_version}" "$(uuid)"
  local subscription_restored_version
  subscription_restored_version="$(jq -er '.data.version' "${response_file}")"
  restore_resource WISH "${wish_id}" "${wish_version}" "$(uuid)"
  local wish_restored_version
  wish_restored_version="$(jq -er '.data.version' "${response_file}")"

  api_request primary GET '/api/v1/recovery/resources?page=1&size=50'
  assert_success 'list recovery resources after full restore'
  jq -e --arg item "${item_id}" --arg subscription "${subscription_id}" \
    --arg wish "${wish_id}" \
    '.data.items | all(.id != $item and .id != $subscription and .id != $wish)' \
    "${response_file}" >/dev/null \
    || fail_api 'restored resources remain in deleted list'

  api_request primary DELETE \
    "/api/v1/items/${item_id}?version=${item_restored_version}" '' "$(uuid)"
  assert_success 'clean restored item through public API'
  api_request primary DELETE \
    "/api/v1/subscriptions/${subscription_id}?version=${subscription_restored_version}" \
    '' "$(uuid)"
  assert_success 'clean restored subscription through public API'
  api_request primary DELETE \
    "/api/v1/wishes/${wish_id}?version=${wish_restored_version}" '' "$(uuid)"
  assert_success 'clean restored wish through public API'
}

main() {
  trap cleanup EXIT
  require_command curl
  require_command jq
  require_command uuidgen
  require_env WORTHIT_AUTH_LOCAL_USERNAME
  require_env WORTHIT_AUTH_LOCAL_PASSWORD
  require_env WORTHIT_AUTH_SECONDARY_USERNAME
  require_env WORTHIT_AUTH_SECONDARY_PASSWORD
  [[ "${wait_seconds}" =~ ^[0-9]+$ && "${wait_seconds}" -ge 61 ]] \
    || fail 'WORTHIT_RECOVERY_WAIT_SECONDS must be an integer >= 61'

  login primary "${WORTHIT_AUTH_LOCAL_USERNAME}" \
    "${WORTHIT_AUTH_LOCAL_PASSWORD}"
  login secondary "${WORTHIT_AUTH_SECONDARY_USERNAME}" \
    "${WORTHIT_AUTH_SECONDARY_PASSWORD}"
  create_resources
  verify_deleted_list_and_isolation
  verify_long_restore_and_cleanup
  printf 'PASS: M3 Tracking full recovery verification completed; run=%s\n' \
    "${run_suffix}"
}

main "$@"
