#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "${script_dir}/../.." && pwd)"
dev_stack_dir="${DEV_STACK_DIR:-/Users/shaopc/Documents/Script/dev-stack}"

nacos_server_base_url="${NACOS_SERVER_BASE_URL:-http://127.0.0.1:8848/nacos}"
nacos_console_base_url="${NACOS_CONSOLE_BASE_URL:-http://127.0.0.1:8080}"
nacos_namespace="${NACOS_NAMESPACE:-worthit-local}"
nacos_group="${NACOS_GROUP:-WORTHIT_LOCAL}"
mysql_host="${WORTHIT_MYSQL_HOST:-127.0.0.1}"
mysql_port="${WORTHIT_MYSQL_PORT:-3306}"

nacos_server_base_url="${nacos_server_base_url%/}"
nacos_console_base_url="${nacos_console_base_url%/}"

curl_flags=(--fail-with-body --silent --show-error)
nacos_auth_header=()
probe_config_changed=false
original_probe_message=""

services=(
  "worthit-gateway:18080"
  "worthit-auth:18081"
  "worthit-tracking:18082"
  "worthit-reminder:18083"
)
databases=(
  "worthit_auth:WORTHIT_AUTH_DB_USERNAME:WORTHIT_AUTH_DB_PASSWORD"
  "worthit_tracking:WORTHIT_TRACKING_DB_USERNAME:WORTHIT_TRACKING_DB_PASSWORD"
  "worthit_reminder:WORTHIT_REMINDER_DB_USERNAME:WORTHIT_REMINDER_DB_PASSWORD"
)

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
  [[ -n "${!1:-}" ]] || fail "required environment variable is missing: $1"
}

assert_json() {
  local description="$1"
  shift
  local argument_count="$#"
  local payload="${!argument_count}"
  local -a jq_arguments=("${@:1:argument_count-1}")
  jq -e "${jq_arguments[@]}" >/dev/null <<<"${payload}" \
    || fail "${description}"
}

assert_no_secret_literal() {
  local description="$1"
  local payload="$2"
  local variable_name
  local secret

  for variable_name in \
    NACOS_PASSWORD \
    WORTHIT_REDIS_PASSWORD \
    WORTHIT_AUTH_DB_PASSWORD \
    WORTHIT_TRACKING_DB_PASSWORD \
    WORTHIT_REMINDER_DB_PASSWORD \
    WORTHIT_SA_TOKEN_JWT_SECRET; do
    secret="${!variable_name:-}"
    if [[ "${variable_name}" == "NACOS_PASSWORD" && "${#secret}" -lt 8 ]]; then
      continue
    fi
    if [[ -n "${secret}" && "${payload}" == *"${secret}"* ]]; then
      fail "${description} contains a configured secret"
    fi
  done
}

configure_nacos_authentication() {
  local username="${NACOS_USERNAME:-}"
  local password="${NACOS_PASSWORD:-}"
  local response
  local access_token

  if [[ -z "${username}" && -z "${password}" ]]; then
    return
  fi
  [[ -n "${username}" && -n "${password}" ]] \
    || fail "NACOS_USERNAME and NACOS_PASSWORD must be provided together"

  response="$(curl "${curl_flags[@]}" \
    --request POST \
    --data-urlencode "username=${username}" \
    --data-urlencode "password=${password}" \
    "${nacos_server_base_url}/v3/auth/user/login")" \
    || fail "Nacos authentication failed"
  access_token="$(jq -er '.accessToken' <<<"${response}")" \
    || fail "Nacos authentication response has no access token"
  nacos_auth_header=(--header "accessToken: ${access_token}")
}

nacos_request() {
  if [[ "${#nacos_auth_header[@]}" -gt 0 ]]; then
    curl "${curl_flags[@]}" "${nacos_auth_header[@]}" "$@"
  else
    curl "${curl_flags[@]}" "$@"
  fi
}

verify_nacos_readiness() {
  local server_response
  local console_response

  server_response="$(nacos_request \
    "${nacos_server_base_url}/v3/admin/core/state/readiness")" \
    || fail "Nacos server readiness endpoint is not ready"
  assert_json "Nacos server readiness returned a non-zero code" \
    '.code == 0' "${server_response}"

  console_response="$(curl "${curl_flags[@]}" \
    "${nacos_console_base_url}/v3/console/health/readiness")" \
    || fail "Nacos console readiness endpoint is not ready"
  assert_json "Nacos console readiness returned a non-zero code" \
    '.code == 0' "${console_response}"
  assert_no_secret_literal "Nacos readiness response" \
    "${server_response}${console_response}"
  pass "Nacos server and console readiness"
}

verify_mysql_migrations() {
  local definition
  local database_name
  local username_variable
  local password_variable
  local username
  local password
  local result

  for definition in "${databases[@]}"; do
    IFS=: read -r database_name username_variable password_variable \
      <<<"${definition}"
    require_env "${username_variable}"
    require_env "${password_variable}"
    username="${!username_variable}"
    password="${!password_variable}"

    result="$(MYSQL_PWD="${password}" mysql \
      --protocol=TCP \
      --host="${mysql_host}" \
      --port="${mysql_port}" \
      --user="${username}" \
      --batch \
      --skip-column-names \
      --execute="
        SELECT COUNT(*)
        FROM flyway_schema_history
        WHERE version = '1' AND success = 1;
      " "${database_name}" 2>/dev/null)" \
      || fail "MySQL database ${database_name} or Flyway history is not ready"
    [[ "${result}" =~ ^[1-9][0-9]*$ ]] \
      || fail "MySQL database ${database_name} has no successful Flyway V1"
    pass "MySQL ${database_name} Flyway V1"
  done
}

redis_cli() {
  (
    cd "${dev_stack_dir}"
    docker compose exec -T \
      -e REDISCLI_AUTH="${WORTHIT_REDIS_PASSWORD}" \
      redis redis-cli --raw "$@"
  )
}

verify_redis() {
  local response
  local key_count

  require_env WORTHIT_REDIS_PASSWORD
  [[ -d "${dev_stack_dir}" ]] \
    || fail "dev-stack directory not found: ${dev_stack_dir}"

  response="$(redis_cli ping 2>/dev/null)" \
    || fail "Redis PING failed"
  [[ "${response}" == "PONG" ]] || fail "Redis PING did not return PONG"

  key_count="$(redis_cli --scan --pattern 'worthit-token:*' 2>/dev/null \
    | awk 'NF { count++ } END { print count + 0 }')" \
    || fail "Redis Sa-Token key scan failed"
  [[ "${key_count}" -gt 0 ]] \
    || fail "Redis has no controlled Sa-Token keys"
  pass "Redis PING and controlled Sa-Token key presence (${key_count} key(s), values hidden)"
}

get_json() {
  local description="$1"
  local url="$2"
  local response

  response="$(curl "${curl_flags[@]}" "${url}")" \
    || fail "${description}"
  assert_no_secret_literal "${description} response" "${response}"
  printf '%s' "${response}"
}

verify_service_health() {
  local definition
  local service_name
  local port
  local health_kind
  local response

  for definition in "${services[@]}"; do
    IFS=: read -r service_name port <<<"${definition}"
    for health_kind in liveness readiness; do
      response="$(get_json \
        "${service_name} ${health_kind} endpoint is not ready" \
        "http://127.0.0.1:${port}/actuator/health/${health_kind}")"
      assert_json "${service_name} ${health_kind} is not UP" \
        '.status == "UP"' "${response}"
    done
    pass "${service_name} liveness and readiness"
  done
}

verify_nacos_instances() {
  local definition
  local service_name
  local expected_port
  local response

  for definition in "${services[@]}"; do
    IFS=: read -r service_name expected_port <<<"${definition}"
    if response="$(nacos_request \
        --get \
        --data-urlencode "namespaceId=${nacos_namespace}" \
        --data-urlencode "groupName=${nacos_group}" \
        --data-urlencode "serviceName=${service_name}" \
        --data-urlencode "healthyOnly=true" \
        "${nacos_server_base_url}/v3/admin/ns/instance/list" \
        2>/dev/null)"; then
      assert_json "Nacos returned a non-zero code for ${service_name}" \
        '.code == 0' "${response}"
      assert_json \
        "Nacos does not have exactly one healthy ${service_name}:${expected_port} instance" \
        --argjson expected_port "${expected_port}" \
        '.data | length == 1
          and .[0].healthy == true
          and .[0].port == $expected_port' "${response}"
      pass "Nacos Admin API healthy instance ${service_name}:${expected_port}"
      continue
    fi

    [[ "${#nacos_auth_header[@]}" -eq 0 ]] \
      || fail "Nacos Admin instance query failed for ${service_name}"
    response="$(curl "${curl_flags[@]}" \
      --get \
      --data-urlencode "namespaceId=${nacos_namespace}" \
      --data-urlencode "groupName=${nacos_group}" \
      --data-urlencode "serviceName=${service_name}" \
      --data-urlencode "healthyOnly=true" \
      "${nacos_server_base_url}/v1/ns/instance/list")" \
      || fail "Nacos Client instance query failed for ${service_name}"
    assert_json \
      "Nacos Client API does not have exactly one healthy ${service_name}:${expected_port} instance" \
      --arg expected_name "${nacos_group}@@${service_name}" \
      --argjson expected_port "${expected_port}" \
      '.name == $expected_name
        and ([.hosts[] | select(
          .healthy == true
          and .enabled == true
          and .port == $expected_port)] | length) == 1' "${response}"
    pass "Nacos Client API healthy instance ${service_name}:${expected_port}"
  done
}

assert_probe() {
  local description="$1"
  local url="$2"
  local expected_service="$3"
  local response

  response="$(get_json "${description}" "${url}")"
  assert_json "${description} returned an unexpected response" \
    --arg expected_service "${expected_service}" \
    '.service == $expected_service and .probe == "ready"' "${response}"
  pass "${description}"
}

verify_discovery_probes() {
  local auth_response

  auth_response="$(get_json \
    "Gateway to Auth readiness probe failed" \
    "http://127.0.0.1:18080/__infra/auth/readiness")"
  assert_json "Gateway to Auth readiness probe is not UP" \
    '.status == "UP"' "${auth_response}"
  pass "Gateway to Auth discovery probe"

  assert_probe \
    "Gateway to Reminder discovery probe" \
    "http://127.0.0.1:18080/__infra/reminder/ping" \
    "worthit-reminder"
  assert_probe \
    "Gateway to Tracking to Reminder discovery probe" \
    "http://127.0.0.1:18080/__infra/tracking/reminder/ping" \
    "worthit-reminder"
}

verify_direct_same_token_rejection() {
  local status
  local response_file
  response_file="$(mktemp)"

  status="$(curl --silent --show-error \
    --output "${response_file}" \
    --write-out '%{http_code}' \
    "http://127.0.0.1:18083/internal/__infra/ping")" \
    || fail "direct missing Same-Token rejection request failed"
  [[ "${status}" == "403" ]] \
    || fail "direct missing Same-Token request returned ${status}, expected 403"
  assert_no_secret_literal "direct missing Same-Token response" \
    "$(tr -d '\000' <"${response_file}")"

  status="$(curl --silent --show-error \
    --output "${response_file}" \
    --write-out '%{http_code}' \
    --header 'SA-SAME-TOKEN: deliberately-wrong-local-probe' \
    "http://127.0.0.1:18083/internal/__infra/ping")" \
    || fail "direct wrong Same-Token rejection request failed"
  [[ "${status}" == "403" ]] \
    || fail "direct wrong Same-Token request returned ${status}, expected 403"
  assert_no_secret_literal "direct wrong Same-Token response" \
    "$(tr -d '\000' <"${response_file}")"

  rm -f "${response_file}"
  pass "direct missing and wrong Same-Token requests are rejected"
}

restore_probe_config() {
  if [[ "${probe_config_changed}" != true || -z "${original_probe_message}" ]]; then
    return
  fi
  set_probe_message_value "${original_probe_message}" \
    >/dev/null 2>&1 || true
}

set_probe_message_value() {
  local probe_message="$1"
  if [[ "${#nacos_auth_header[@]}" -gt 0 ]]; then
    WORTHIT_PROBE_MESSAGE="${probe_message}" \
      "${script_dir}/nacos-config.sh" set-probe-message >/dev/null
    return
  fi

  local content
  local updated_content
  local response
  content="$(curl "${curl_flags[@]}" \
    --get \
    --data-urlencode 'dataId=worthit-common.yaml' \
    --data-urlencode "group=${nacos_group}" \
    --data-urlencode "tenant=${nacos_namespace}" \
    "${nacos_server_base_url}/v1/cs/configs")" \
    || fail "Nacos Client config read failed"
  [[ "$(grep -c '^[[:space:]]*probe-message:' <<<"${content}")" -eq 1 ]] \
    || fail "worthit-common.yaml must contain exactly one probe-message"
  updated_content="$(awk -v value="${probe_message}" '
    /^[[:space:]]*probe-message:/ {
      sub(/probe-message:.*/, "probe-message: " value)
    }
    { print }
  ' <<<"${content}")"
  updated_content+=$'\n'
  response="$(curl "${curl_flags[@]}" \
    --request POST \
    --data-urlencode 'dataId=worthit-common.yaml' \
    --data-urlencode "group=${nacos_group}" \
    --data-urlencode "tenant=${nacos_namespace}" \
    --data-urlencode "content=${updated_content}" \
    --data-urlencode 'type=yaml' \
    "${nacos_server_base_url}/v1/cs/configs")" \
    || fail "Nacos Client config publish failed"
  [[ "${response}" == "true" ]] \
    || fail "Nacos Client config publish was rejected"
}

verify_config_refresh() {
  local updated_probe_message="phase0-verify-$(date +%s)"
  local response
  local attempt

  original_probe_message="$(awk '
    /^[[:space:]]*probe-message:/ {
      print $2
      exit
    }
  ' "${repository_root}/deploy/nacos/local/worthit-common.yaml")"
  [[ -n "${original_probe_message}" ]] \
    || fail "local worthit-common template has no probe-message"

  set_probe_message_value "${updated_probe_message}" >/dev/null \
    || fail "Nacos probe-message update failed"
  probe_config_changed=true

  for attempt in {1..30}; do
    response="$(curl --silent --show-error \
      "http://127.0.0.1:18080/__infra/tracking/config" || true)"
    if jq -e --arg expected "${updated_probe_message}" \
      '.probeMessage == $expected' >/dev/null 2>&1 <<<"${response}"; then
      assert_no_secret_literal "refreshed config response" "${response}"
      pass "Nacos probe-message refresh reached Tracking through Gateway"
      restore_probe_config
      probe_config_changed=false
      return
    fi
    sleep 1
  done
  fail "Nacos probe-message refresh did not reach Tracking within 30 seconds"
}

verify_logs_have_no_configured_secrets() {
  local log_dir="${WORTHIT_LOG_DIR:-}"
  local log_file
  local variable_name
  local secret

  [[ -n "${log_dir}" ]] || fail "WORTHIT_LOG_DIR must be provided for log secret scanning"
  [[ -d "${log_dir}" ]] || fail "WORTHIT_LOG_DIR does not exist"

  while IFS= read -r -d '' log_file; do
    for variable_name in \
      NACOS_PASSWORD \
      WORTHIT_REDIS_PASSWORD \
      WORTHIT_AUTH_DB_PASSWORD \
      WORTHIT_TRACKING_DB_PASSWORD \
      WORTHIT_REMINDER_DB_PASSWORD \
      WORTHIT_SA_TOKEN_JWT_SECRET; do
      secret="${!variable_name:-}"
      if [[ "${variable_name}" == "NACOS_PASSWORD" && "${#secret}" -lt 8 ]]; then
        continue
      fi
      if [[ -n "${secret}" ]] && grep -Fq -- "${secret}" "${log_file}"; then
        fail "application logs contain a configured secret"
      fi
    done
    if grep -Eiq \
      '(SA-SAME-TOKEN|Authorization|jwt-secret-key)[[:space:]]*[:=][[:space:]]*[^[:space:]]+' \
      "${log_file}"; then
      fail "application logs contain a security header or JWT secret value"
    fi
  done < <(find "${log_dir}" -type f -name '*.log' -print0)
  pass "application logs contain no configured password, JWT secret, or Same-Token value"
}

main() {
  trap restore_probe_config EXIT

  require_command curl
  require_command jq
  require_command mysql
  require_command docker
  require_command awk
  require_command grep
  require_command find

  configure_nacos_authentication
  verify_nacos_readiness
  verify_mysql_migrations
  verify_service_health
  verify_nacos_instances
  verify_redis
  verify_discovery_probes
  verify_direct_same_token_rejection
  verify_config_refresh
  verify_logs_have_no_configured_secrets
  printf 'PASS: local infrastructure verification completed\n'
}

main "$@"
