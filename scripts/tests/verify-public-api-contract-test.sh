#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
subject="${repo_root}/scripts/local-infra/verify-public-api.sh"

bash -n "${subject}"

required_contracts=(
  'WORTHIT_AUTH_LOCAL_USERNAME'
  'WORTHIT_AUTH_LOCAL_PASSWORD'
  'Authorization: Bearer'
  '/api/v1/auth/password/login'
  '/api/v1/auth/me'
  'PATCH "/api/v1/categories/${category_id}"'
  'external forged internal token is rejected'
)

for contract in "${required_contracts[@]}"; do
  grep -Fq -- "${contract}" "${subject}" \
    || { printf 'Missing public API verification contract: %s\n' "${contract}" >&2; exit 1; }
done

if grep -Eq 'printf .*WORTHIT_AUTH_LOCAL_PASSWORD' \
  "${subject}"; then
  printf 'Public API verification script may print a credential.\n' >&2
  exit 1
fi

grep -Fq '"${token}" >>"${header_file}"' "${subject}" \
  || { printf 'Bearer credential must be written only to the protected header file.\n' >&2; exit 1; }

printf 'verify-public-api contract tests: OK\n'
