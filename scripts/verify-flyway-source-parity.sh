#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/.." && pwd)"
default_docs_dir="${repo_root}/../docs"

if [[ ! -d "${default_docs_dir}" ]]; then
  git_common_dir="$(git -C "${repo_root}" rev-parse --git-common-dir)"
  if [[ "${git_common_dir}" != /* ]]; then
    git_common_dir="${repo_root}/${git_common_dir}"
  fi
  primary_repo_root="$(cd "${git_common_dir}/.." && pwd)"
  default_docs_dir="${primary_repo_root}/../docs"
fi

docs_dir="${WORTHIT_DOCS_DIR:-${default_docs_dir}}"

compare_source() {
  local service="$1"
  local module_path="$2"
  local file_name="$3"
  local source_file="${docs_dir}/数据库文档/flyway/${service}/${file_name}"
  local runtime_file="${repo_root}/${module_path}/src/main/resources/db/migration/${file_name}"

  if [[ ! -f "${source_file}" ]]; then
    printf 'Missing authoritative Flyway source: %s\n' "${source_file}" >&2
    return 1
  fi
  if [[ ! -f "${runtime_file}" ]]; then
    printf 'Missing runtime Flyway source: %s\n' "${runtime_file}" >&2
    return 1
  fi
  if ! cmp -s "${source_file}" "${runtime_file}"; then
    printf '%s: MISMATCH\n' "${file_name}" >&2
    return 1
  fi

  printf '%s: OK\n' "${file_name}"
}

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
