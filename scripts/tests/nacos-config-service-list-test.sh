#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
subject="${repo_root}/scripts/local-infra/nacos-config.sh"
fixture="${script_dir}/fixtures/nacos-config/fake-curl.sh"
fake_bin="$(mktemp -d "${TMPDIR:-/tmp}/worthit-nacos-test.XXXXXX")"

cleanup() {
  find "${fake_bin}" -depth -delete
}
trap cleanup EXIT

ln -s "${fixture}" "${fake_bin}/curl"

run_services() {
  local mode="$1"
  env \
    -u NACOS_USERNAME \
    -u NACOS_PASSWORD \
    -u NACOS_SERVER_BASE_URL \
    -u NACOS_CONSOLE_BASE_URL \
    PATH="${fake_bin}:${PATH}" \
    FAKE_NACOS_MODE="${mode}" \
    "${subject}" services
}

assert_contains() {
  local output="$1"
  local expected="$2"
  if [[ "${output}" != *"${expected}"* ]]; then
    printf 'Expected output to contain: %s\nActual output:\n%s\n' \
      "${expected}" "${output}" >&2
    exit 1
  fi
}

bash -n "${subject}"
bash -n "${fixture}"

if ! output="$(run_services missing-service 2>&1)"; then
  printf 'A missing Nacos service must be reported as zero instances.\n%s\n' \
    "${output}" >&2
  exit 1
fi
assert_contains "${output}" \
  'worthit-gateway: 0 healthy instance(s) (not registered)'
assert_contains "${output}" 'worthit-auth: 2 healthy instance(s)'
assert_contains "${output}" 'worthit-tracking: 1 healthy instance(s)'
assert_contains "${output}" 'worthit-reminder: 0 healthy instance(s)'

if output="$(run_services unexpected-404 2>&1)"; then
  printf 'An unexpected HTTP 404 must fail service diagnostics.\n' >&2
  exit 1
fi
assert_contains "${output}" \
  'List worthit-gateway failed: unexpected HTTP 404 response'

if output="$(run_services server-error 2>&1)"; then
  printf 'An HTTP 500 must fail service diagnostics.\n' >&2
  exit 1
fi
assert_contains "${output}" \
  'List worthit-gateway failed: unexpected HTTP 500 response'

if output="$(run_services transport-error 2>&1)"; then
  printf 'A transport error must fail service diagnostics.\n' >&2
  exit 1
fi
assert_contains "${output}" \
  'List worthit-gateway failed: request transport error'

if output="$(run_services malformed-success 2>&1)"; then
  printf 'A successful response with non-array data must fail diagnostics.\n' >&2
  exit 1
fi
assert_contains "${output}" \
  'List worthit-gateway failed: response data is not an array'

printf 'nacos-config service-list tests: OK\n'
