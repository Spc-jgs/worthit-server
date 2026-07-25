#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "${script_dir}/../.." && pwd)"
template_dir="${repository_root}/deploy/nacos/local"

nacos_server_base_url="${NACOS_SERVER_BASE_URL:-http://127.0.0.1:8848/nacos}"
nacos_console_base_url="${NACOS_CONSOLE_BASE_URL:-http://127.0.0.1:8080}"
nacos_namespace="${NACOS_NAMESPACE:-worthit-local}"
nacos_group="${NACOS_GROUP:-WORTHIT_LOCAL}"

nacos_server_base_url="${nacos_server_base_url%/}"
nacos_console_base_url="${nacos_console_base_url%/}"

data_ids=(
  worthit-common.yaml
  worthit-gateway.yaml
  worthit-auth.yaml
  worthit-tracking.yaml
  worthit-reminder.yaml
)
services=(
  worthit-gateway
  worthit-auth
  worthit-tracking
  worthit-reminder
)
curl_flags=(--fail-with-body --silent --show-error)
auth_header=()

require_command() {
  local command_name="$1"
  if ! command -v "${command_name}" >/dev/null 2>&1; then
    printf 'Required command not found: %s\n' "${command_name}" >&2
    exit 1
  fi
}

check_result_code() {
  local operation="$1"
  local response="$2"
  if ! jq -e '.code == 0' >/dev/null <<<"${response}"; then
    printf '%s failed: Nacos returned a non-zero code\n' "${operation}" >&2
    exit 1
  fi
}

configure_authentication() {
  local username="${NACOS_USERNAME:-}"
  local password="${NACOS_PASSWORD:-}"

  if [[ -z "${username}" && -z "${password}" ]]; then
    return
  fi
  if [[ -z "${username}" || -z "${password}" ]]; then
    printf 'NACOS_USERNAME and NACOS_PASSWORD must be provided together\n' >&2
    exit 1
  fi

  local response
  local access_token
  response="$(curl "${curl_flags[@]}" \
    --request POST \
    --data-urlencode "username=${username}" \
    --data-urlencode "password=${password}" \
    "${nacos_server_base_url}/v3/auth/user/login")"
  access_token="$(jq -er '.accessToken' <<<"${response}")"
  auth_header=(--header "accessToken: ${access_token}")
}

server_request() {
  curl "${curl_flags[@]}" "${auth_header[@]}" "$@"
}

check_readiness() {
  local server_response
  local console_response
  server_response="$(server_request \
    "${nacos_server_base_url}/v3/admin/core/state/readiness")"
  check_result_code "Nacos server readiness" "${server_response}"

  console_response="$(curl "${curl_flags[@]}" \
    "${nacos_console_base_url}/v3/console/health/readiness")"
  check_result_code "Nacos console readiness" "${console_response}"
  printf 'Nacos server and console: READY\n'
}

namespace_exists() {
  local response
  response="$(server_request \
    --get \
    --data-urlencode "namespaceId=${nacos_namespace}" \
    "${nacos_server_base_url}/v3/admin/core/namespace/check")"
  check_result_code "Namespace check" "${response}"
  jq -e '.data > 0' >/dev/null <<<"${response}"
}

ensure_namespace() {
  if namespace_exists; then
    printf 'Namespace %s: EXISTS\n' "${nacos_namespace}"
    return
  fi

  local response
  response="$(server_request \
    --request POST \
    --data-urlencode "namespaceId=${nacos_namespace}" \
    --data-urlencode "namespaceName=${nacos_namespace}" \
    --data-urlencode "namespaceDesc=WorthIt local infrastructure" \
    "${nacos_server_base_url}/v3/admin/core/namespace")"
  check_result_code "Namespace create" "${response}"
  if ! jq -e '.data == true' >/dev/null <<<"${response}"; then
    printf 'Namespace create failed\n' >&2
    exit 1
  fi
  printf 'Namespace %s: CREATED\n' "${nacos_namespace}"
}

publish_config() {
  local data_id="$1"
  local template="${template_dir}/${data_id}"
  if [[ ! -f "${template}" ]]; then
    printf 'Template not found: %s\n' "${data_id}" >&2
    exit 1
  fi

  local response
  response="$(server_request \
    --request POST \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode "groupName=${nacos_group}" \
    --data-urlencode "namespaceId=${nacos_namespace}" \
    --data-urlencode "content@${template}" \
    --data-urlencode "type=yaml" \
    "${nacos_server_base_url}/v3/admin/cs/config")"
  check_result_code "Publish ${data_id}" "${response}"
  if ! jq -e '.data == true' >/dev/null <<<"${response}"; then
    printf '%s: publish rejected\n' "${data_id}" >&2
    exit 1
  fi
  printf '%s: SYNCED\n' "${data_id}"
}

verify_config() {
  local data_id="$1"
  local template="${template_dir}/${data_id}"
  local response
  response="$(server_request \
    --get \
    --data-urlencode "dataId=${data_id}" \
    --data-urlencode "groupName=${nacos_group}" \
    --data-urlencode "namespaceId=${nacos_namespace}" \
    "${nacos_server_base_url}/v3/admin/cs/config")"
  check_result_code "Read ${data_id}" "${response}"

  if ! cmp -s "${template}" <(jq -j '.data.content' <<<"${response}"); then
    printf '%s: MISMATCH\n' "${data_id}" >&2
    exit 1
  fi
  printf '%s: VERIFIED\n' "${data_id}"
}

list_service() {
  local service_name="$1"
  local response
  local healthy_count
  response="$(server_request \
    --get \
    --data-urlencode "namespaceId=${nacos_namespace}" \
    --data-urlencode "groupName=${nacos_group}" \
    --data-urlencode "serviceName=${service_name}" \
    --data-urlencode "healthyOnly=true" \
    "${nacos_server_base_url}/v3/admin/ns/instance/list")"
  check_result_code "List ${service_name}" "${response}"
  healthy_count="$(jq -er '.data | length' <<<"${response}")"
  printf '%s: %s healthy instance(s)\n' "${service_name}" "${healthy_count}"
}

usage() {
  printf 'Usage: %s {check|sync|verify|services}\n' "$0" >&2
  exit 2
}

main() {
  require_command curl
  require_command jq
  configure_authentication

  case "${1:-}" in
    check)
      check_readiness
      ;;
    sync)
      check_readiness
      ensure_namespace
      for data_id in "${data_ids[@]}"; do
        publish_config "${data_id}"
      done
      ;;
    verify)
      check_readiness
      for data_id in "${data_ids[@]}"; do
        verify_config "${data_id}"
      done
      ;;
    services)
      check_readiness
      for service_name in "${services[@]}"; do
        list_service "${service_name}"
      done
      ;;
    *)
      usage
      ;;
  esac
}

main "$@"
