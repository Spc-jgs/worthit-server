#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="${WORTHIT_REPO_ROOT:-$(cd "${script_dir}/.." && pwd)}"
default_docs_dir="${repo_root}/../docs"
lock_file="${WORTHIT_FLYWAY_LOCK_FILE:-${script_dir}/flyway-authority.sha256}"

if [[ -z "${WORTHIT_DOCS_DIR:-}" && ! -d "${default_docs_dir}" ]]; then
  git_common_dir="$(git -C "${repo_root}" rev-parse --git-common-dir)"
  if [[ "${git_common_dir}" != /* ]]; then
    git_common_dir="${repo_root}/${git_common_dir}"
  fi
  primary_repo_root="$(cd "${git_common_dir}/.." && pwd)"
  default_docs_dir="${primary_repo_root}/../docs"
fi

docs_dir="${WORTHIT_DOCS_DIR:-${default_docs_dir}}"

sha256_file() {
  local file_path="$1"

  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${file_path}" | awk '{print $1}'
    return
  fi
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${file_path}" | awk '{print $1}'
    return
  fi

  printf 'Neither sha256sum nor shasum is available.\n' >&2
  return 1
}

locked_checksum() {
  local service="$1"
  local runtime_path="$2"

  awk -v service="${service}" -v runtime_path="${runtime_path}" \
    '$1 !~ /^#/ && $2 == service && $3 == runtime_path { print $1 }' \
    "${lock_file}"
}

compare_source() {
  local service="$1"
  local module_path="$2"
  local file_name="$3"
  local runtime_path="${module_path}/src/main/resources/db/migration/${file_name}"
  local source_file="${docs_dir}/数据库文档/flyway/${service}/${file_name}"
  local runtime_file="${repo_root}/${runtime_path}"
  local expected_checksum
  local actual_checksum

  if [[ ! -f "${runtime_file}" ]]; then
    printf 'Missing runtime Flyway source: %s\n' "${runtime_file}" >&2
    return 1
  fi
  expected_checksum="$(locked_checksum "${service}" "${runtime_path}")"
  if [[ -z "${expected_checksum}" ]]; then
    printf 'Missing locked Flyway checksum: %s\n' "${runtime_path}" >&2
    return 1
  fi
  actual_checksum="$(sha256_file "${runtime_file}")"
  if [[ "${actual_checksum}" != "${expected_checksum}" ]]; then
    printf '%s: CHECKSUM MISMATCH (expected %s, actual %s)\n' \
      "${file_name}" "${expected_checksum}" "${actual_checksum}" >&2
    return 1
  fi

  if [[ ! -d "${docs_dir}" ]]; then
    printf '%s: OK (locked digest; authoritative docs unavailable)\n' "${file_name}"
    return
  fi
  if [[ ! -f "${source_file}" ]]; then
    printf 'Missing authoritative Flyway source: %s\n' "${source_file}" >&2
    return 1
  fi
  if ! cmp -s "${source_file}" "${runtime_file}"; then
    printf '%s: SOURCE MISMATCH\n' "${file_name}" >&2
    return 1
  fi

  printf '%s: OK (authoritative source and locked digest)\n' "${file_name}"
}

if [[ ! -f "${lock_file}" ]]; then
  printf 'Missing Flyway checksum lock: %s\n' "${lock_file}" >&2
  exit 1
fi

compare_source \
  auth \
  worthit-auth/worthit-auth-app \
  V1__init_auth.sql
compare_source \
  tracking \
  worthit-tracking/worthit-tracking-app \
  V1__init_tracking.sql
compare_source \
  reminder \
  worthit-reminder/worthit-reminder-app \
  V1__init_reminder.sql
