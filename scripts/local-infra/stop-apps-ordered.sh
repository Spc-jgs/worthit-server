#!/usr/bin/env bash

set -euo pipefail

shutdown_timeout_seconds="${WORTHIT_SHUTDOWN_TIMEOUT_SECONDS:-30}"
nacos_server_base_url="${NACOS_SERVER_BASE_URL:-http://127.0.0.1:8848/nacos}"
nacos_namespace="${NACOS_NAMESPACE:-worthit-local}"
nacos_group="${NACOS_GROUP:-WORTHIT_LOCAL}"
nacos_server_base_url="${nacos_server_base_url%/}"

fail() {
  printf 'FAIL: %s\n' "$1" >&2
  exit 1
}

require_pid() {
  local variable_name="$1"
  local expected_jar="$2"
  local pid="${!variable_name:-}"
  local command_line

  [[ "${pid}" =~ ^[1-9][0-9]*$ ]] \
    || fail "${variable_name} must be a positive process id"
  kill -0 "${pid}" 2>/dev/null \
    || fail "${variable_name} process is not running"
  command_line="$(ps -p "${pid}" -o command=)"
  [[ "${command_line}" == *"${expected_jar}"* ]] \
    || fail "${variable_name} does not belong to ${expected_jar}"
}

stop_one() {
  local service="$1"
  local pid="$2"
  local waited=0

  kill -TERM "${pid}"
  while kill -0 "${pid}" 2>/dev/null \
      && [[ "$(ps -p "${pid}" -o stat= 2>/dev/null)" != Z* ]]; do
    if (( waited >= shutdown_timeout_seconds * 10 )); then
      fail "${service} did not stop within ${shutdown_timeout_seconds}s"
    fi
    sleep 0.1
    waited=$((waited + 1))
  done
  wait "${pid}" 2>/dev/null || true
  printf 'PASS: %s stopped after SIGTERM\n' "${service}"
}

assert_port_released() {
  local service="$1"
  local port="$2"
  if curl --silent --output /dev/null --max-time 1 \
      "http://127.0.0.1:${port}/actuator/health/liveness"; then
    fail "${service} port ${port} is still accepting HTTP connections"
  fi
  printf 'PASS: %s port %s released\n' "${service}" "${port}"
}

wait_for_nacos_unregister() {
  local service="$1"
  local attempt
  local count
  for attempt in {1..30}; do
    count="$(curl --fail-with-body --silent --show-error \
      --get \
      --data-urlencode "serviceName=${service}" \
      --data-urlencode "groupName=${nacos_group}" \
      --data-urlencode "namespaceId=${nacos_namespace}" \
      --data-urlencode 'healthyOnly=false' \
      "${nacos_server_base_url}/v1/ns/instance/list" \
      | jq '[.hosts[]? | select(.enabled == true)] | length')" \
      || fail "Nacos instance query failed for ${service}"
    if [[ "${count}" == 0 ]]; then
      printf 'PASS: %s unregistered from Nacos\n' "${service}"
      return
    fi
    sleep 1
  done
  fail "${service} remains registered in Nacos after shutdown"
}

verify_shutdown_logs() {
  local log_dir="${WORTHIT_LOG_DIR:-}"
  local service
  local log_file
  local unexpected_errors
  local known_noise_count=0
  local macos_dns_fallback_count=0

  [[ -n "${log_dir}" && -d "${log_dir}" ]] \
    || fail 'WORTHIT_LOG_DIR must point to the current run logs'
  for service in gateway tracking reminder auth; do
    log_file="${log_dir}/${service}.log"
    [[ -f "${log_file}" ]] \
      || fail "shutdown log is missing for ${service}"
    unexpected_errors="$(rg ' ERROR ' "${log_file}" \
      | rg -v 'c\.a\.nacos\.common\.notify\.NotifyCenter +: Event listener exception :[[:space:]]*$|c\.a\.c\.n\.r\.NacosGracefulShutdownDelegate +: Error occurred while performing Nacos client graceful shutdown[[:space:]]*$|i\.n\.r\.d\.DnsServerAddressStreamProviders +: Unable to load io\.netty\.resolver\.dns\.macos\.MacOSDnsServerAddressStreamProvider, fallback to system defaults\.' \
      || true)"
    [[ -z "${unexpected_errors}" ]] \
      || fail "${service} log contains an unexpected ERROR during the run"
    if rg -q 'NacosGracefulShutdownDelegate' "${log_file}" \
        && rg -q 'NotifyCenter.INSTANCE.*null|java.lang.InterruptedException' \
          "${log_file}"; then
      known_noise_count=$((known_noise_count + 1))
    fi
    if rg -q 'DnsServerAddressStreamProviders.*MacOSDnsServerAddressStreamProvider' \
        "${log_file}"; then
      macos_dns_fallback_count=$((macos_dns_fallback_count + 1))
    fi
  done
  if (( known_noise_count > 0 )); then
    printf 'WARN: known Nacos client shutdown noise observed in %s service log(s); ports and registrations still closed cleanly\n' \
      "${known_noise_count}"
  fi
  if (( macos_dns_fallback_count > 0 )); then
    printf 'WARN: Netty used the system DNS fallback on macOS in %s service log(s)\n' \
      "${macos_dns_fallback_count}"
  fi
  printf 'PASS: shutdown logs contain no unexpected ERROR lines\n'
}

verify_stopped_state() {
  assert_port_released gateway 18080
  assert_port_released tracking 18082
  assert_port_released reminder 18083
  assert_port_released auth 18081

  wait_for_nacos_unregister worthit-gateway
  wait_for_nacos_unregister worthit-tracking
  wait_for_nacos_unregister worthit-reminder
  wait_for_nacos_unregister worthit-auth
  verify_shutdown_logs
}

main() {
  command -v curl >/dev/null 2>&1 || fail 'curl is required'
  command -v jq >/dev/null 2>&1 || fail 'jq is required'
  command -v rg >/dev/null 2>&1 || fail 'rg is required'
  if [[ "${1:-}" == '--verify-stopped' ]]; then
    verify_stopped_state
    printf 'PASS: WorthIt stopped state verified\n'
    return
  fi
  require_pid WORTHIT_GATEWAY_PID \
    'worthit-gateway-0.1.0-SNAPSHOT.jar'
  require_pid WORTHIT_TRACKING_PID \
    'worthit-tracking-app-0.1.0-SNAPSHOT.jar'
  require_pid WORTHIT_REMINDER_PID \
    'worthit-reminder-app-0.1.0-SNAPSHOT.jar'
  require_pid WORTHIT_AUTH_PID \
    'worthit-auth-app-0.1.0-SNAPSHOT.jar'

  stop_one gateway "${WORTHIT_GATEWAY_PID}"
  stop_one tracking "${WORTHIT_TRACKING_PID}"
  stop_one reminder "${WORTHIT_REMINDER_PID}"
  stop_one auth "${WORTHIT_AUTH_PID}"

  verify_stopped_state

  printf 'PASS: WorthIt applications stopped in dependency order\n'
}

main "$@"
