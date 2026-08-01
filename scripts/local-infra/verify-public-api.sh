#!/usr/bin/env bash

set -euo pipefail

gateway_base_url="${WORTHIT_GATEWAY_BASE_URL:-http://127.0.0.1:18080}"
gateway_base_url="${gateway_base_url%/}"
temporary_dir="$(mktemp -d)"
response_file="${temporary_dir}/response.json"
header_file="${temporary_dir}/headers.txt"
token=""
category_id=""
category_deleted=false
http_status=""

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

write_headers() {
  : >"${header_file}"
  chmod 600 "${header_file}"
  printf 'Content-Type: application/json\n' >>"${header_file}"
  if [[ -n "${token}" ]]; then
    printf 'Authorization: Bearer %s\n' "${token}" >>"${header_file}"
  fi
}

api_request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"

  write_headers
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

fail_api() {
  local description="$1"
  local code
  local trace_id
  code="$(jq -r '.code // "UNKNOWN"' "${response_file}" 2>/dev/null \
    || printf 'INVALID_JSON')"
  trace_id="$(jq -r '.traceId // "missing"' "${response_file}" 2>/dev/null \
    || printf 'missing')"
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

cleanup() {
  if [[ -n "${token}" && -n "${category_id}" \
      && "${category_deleted}" != true ]]; then
    write_headers
    curl --silent --output /dev/null \
      --request DELETE \
      --header "@${header_file}" \
      "${gateway_base_url}/api/v1/categories/${category_id}" || true
  fi
  token=""
  rm -rf "${temporary_dir}"
}

verify_forged_internal_token_rejected() {
  printf 'worthit-token: deliberately-forged-public-token\n' >"${header_file}"
  chmod 600 "${header_file}"
  http_status="$(curl --silent --show-error \
    --output "${response_file}" \
    --write-out '%{http_code}' \
    --header "@${header_file}" \
    "${gateway_base_url}/api/v1/auth/me")" \
    || fail "forged internal token probe failed"
  [[ "${http_status}" == "401" ]] \
    || fail_api "forged internal token was not rejected"
  jq -e '.success == false and .code == "AUTH_UNAUTHORIZED"' \
    "${response_file}" >/dev/null \
    || fail_api "forged internal token returned an invalid error envelope"
  pass "external forged internal token is rejected"
}

main() {
  trap cleanup EXIT
  require_command curl
  require_command jq
  require_env WORTHIT_AUTH_LOCAL_USERNAME
  require_env WORTHIT_AUTH_LOCAL_PASSWORD

  verify_forged_internal_token_rejected

  local login_body
  login_body="$(jq -cn \
    --arg username "${WORTHIT_AUTH_LOCAL_USERNAME}" \
    --arg password "${WORTHIT_AUTH_LOCAL_PASSWORD}" \
    '{username:$username,password:$password}')"
  api_request POST "/api/v1/auth/password/login" "${login_body}"
  assert_success "password login through Gateway"
  token="$(jq -er '.data.token' "${response_file}")" \
    || fail "password login response has no token"
  [[ "$(jq -r '.data.tokenType' "${response_file}")" == "Bearer" ]] \
    || fail "password login response tokenType is not Bearer"

  api_request GET "/api/v1/auth/me"
  assert_success "Bearer-only current-user request"

  local category_name
  local renamed_name
  category_name="联调-$(date +%s)-$$"
  renamed_name="已改名-$(date +%s)-$$"
  api_request POST "/api/v1/categories" \
    "$(jq -cn --arg name "${category_name}" '{name:$name}')"
  assert_success "create integration category"
  category_id="$(jq -er '.data.id' "${response_file}")" \
    || fail "category create response has no string id"
  [[ "${category_id}" =~ ^[1-9][0-9]*$ ]] \
    || fail "category create response id is invalid"

  api_request PATCH "/api/v1/categories/${category_id}" \
    "$(jq -cn --arg name "${renamed_name}" '{name:$name}')"
  assert_success "rename integration category"
  jq -e --arg id "${category_id}" --arg name "${renamed_name}" \
    '.data.id == $id and .data.name == $name and .data.deletable == true' \
    "${response_file}" >/dev/null \
    || fail_api "renamed category response is inconsistent"

  api_request GET "/api/v1/categories"
  assert_success "list categories after rename"
  jq -e --arg id "${category_id}" --arg name "${renamed_name}" \
    '.data | any(.id == $id and .name == $name)' \
    "${response_file}" >/dev/null \
    || fail_api "renamed category is absent from list"

  api_request DELETE "/api/v1/categories/${category_id}"
  assert_success "delete unused integration category"
  category_deleted=true

  api_request GET "/api/v1/categories"
  assert_success "list categories after delete"
  jq -e --arg id "${category_id}" \
    '.data | all(.id != $id)' "${response_file}" >/dev/null \
    || fail_api "deleted category remains visible"

  printf 'PASS: public API verification completed with Bearer-only authentication\n'
}

main "$@"
