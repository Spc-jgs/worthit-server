#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
parity_script="${repo_root}/scripts/verify-flyway-source-parity.sh"
fixture_root="$(mktemp -d)"
trap 'rm -rf "${fixture_root}"' EXIT

missing_docs_dir="${fixture_root}/missing-docs"
runtime_root="${fixture_root}/runtime"
mkdir -p "${runtime_root}"

runtime_files=(
  "worthit-auth/worthit-auth-app/src/main/resources/db/migration/V1__init_auth.sql"
  "worthit-auth/worthit-auth-app/src/main/resources/db/migration/V2__add_password_credential.sql"
  "worthit-auth/worthit-auth-app/src/main/resources/db/migration/V3__add_account_cancellation_execution.sql"
  "worthit-tracking/worthit-tracking-app/src/main/resources/db/migration/V1__init_tracking.sql"
  "worthit-tracking/worthit-tracking-app/src/main/resources/db/migration/V2__add_item_lifecycle.sql"
  "worthit-tracking/worthit-tracking-app/src/main/resources/db/migration/V3__add_user_write_fence.sql"
  "worthit-reminder/worthit-reminder-app/src/main/resources/db/migration/V1__init_reminder.sql"
  "worthit-reminder/worthit-reminder-app/src/main/resources/db/migration/V2__add_user_write_fence.sql"
)

for relative_path in "${runtime_files[@]}"; do
  mkdir -p "${runtime_root}/$(dirname "${relative_path}")"
  cp "${repo_root}/${relative_path}" "${runtime_root}/${relative_path}"
done

locked_output="$(
  WORTHIT_REPO_ROOT="${runtime_root}" \
  WORTHIT_DOCS_DIR="${missing_docs_dir}" \
    bash "${parity_script}"
)"
if [[ "${locked_output}" != *"locked digest"* ]]; then
  printf 'Expected locked-digest verification when authoritative docs are unavailable.\n' >&2
  exit 1
fi

printf '\n-- tampered --\n' >> \
  "${runtime_root}/worthit-auth/worthit-auth-app/src/main/resources/db/migration/V1__init_auth.sql"
if WORTHIT_REPO_ROOT="${runtime_root}" \
  WORTHIT_DOCS_DIR="${missing_docs_dir}" \
  bash "${parity_script}" >"${fixture_root}/tampered.out" 2>&1; then
  printf 'Expected tampered runtime migration to fail locked-digest verification.\n' >&2
  exit 1
fi
if ! grep -q "CHECKSUM MISMATCH" "${fixture_root}/tampered.out"; then
  printf 'Expected an actionable checksum mismatch error.\n' >&2
  exit 1
fi

printf 'verify-flyway-source-parity tests: OK\n'
