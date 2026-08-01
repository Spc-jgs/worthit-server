#!/usr/bin/env bash

set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "${script_dir}/../.." && pwd)"
subject="${repo_root}/scripts/local-infra/verify-release-candidate.sh"
shutdown_subject="${repo_root}/scripts/local-infra/stop-apps-ordered.sh"
infra_subject="${repo_root}/scripts/local-infra/verify.sh"

[[ -f "${subject}" ]] \
  || { printf 'Missing release-candidate verifier: %s\n' "${subject}" >&2; exit 1; }
[[ -f "${shutdown_subject}" ]] \
  || { printf 'Missing ordered shutdown helper: %s\n' "${shutdown_subject}" >&2; exit 1; }

bash -n "${subject}"
bash -n "${shutdown_subject}"

required_contracts=(
  'WORTHIT_AUTH_LOCAL_USERNAME'
  'WORTHIT_AUTH_LOCAL_PASSWORD'
  'WORTHIT_AUTH_SECONDARY_USERNAME'
  'WORTHIT_AUTH_SECONDARY_PASSWORD'
  'WORTHIT_REMINDER_DB_USERNAME'
  'WORTHIT_REMINDER_DB_PASSWORD'
  'Authorization: Bearer'
  '/api/v1/items'
  '/api/v1/subscriptions'
  '/api/v1/wishes'
  '/api/v1/dashboard'
  '/api/v1/reminders/pending-count'
  '/api/v1/lifecycle/review'
  'IDEM_CONFLICT'
  'RES_NOT_FOUND'
  'trap cleanup EXIT'
  'materialize_due_reminder_fixture'
  'Reminder fixture businessId must be a positive integer'
)

for contract in "${required_contracts[@]}"; do
  grep -Fq -- "${contract}" "${subject}" \
    || { printf 'Missing release-candidate contract: %s\n' "${contract}" >&2; exit 1; }
done

if grep -Eq 'printf .*\$\{?(primary_password|secondary_password)\}?' \
  "${subject}"; then
  printf 'Release-candidate verifier may print a password variable.\n' >&2
  exit 1
fi

grep -Fq '"${token}" >>"${header_file}"' "${subject}" \
  || { printf 'Bearer token must only be written to the protected header file.\n' >&2; exit 1; }

for client_api in '/v1/ns/instance/list' '/v1/cs/configs'; do
  grep -Fq -- "${client_api}" "${infra_subject}" \
    || { printf 'Missing Nacos client API fallback: %s\n' "${client_api}" >&2; exit 1; }
done

shutdown_contracts=(
  'WORTHIT_GATEWAY_PID'
  'WORTHIT_TRACKING_PID'
  'WORTHIT_REMINDER_PID'
  'WORTHIT_AUTH_PID'
  'worthit-gateway-0.1.0-SNAPSHOT.jar'
  'worthit-tracking-app-0.1.0-SNAPSHOT.jar'
  'worthit-reminder-app-0.1.0-SNAPSHOT.jar'
  'worthit-auth-app-0.1.0-SNAPSHOT.jar'
  'kill -TERM'
  '/v1/ns/instance/list'
  'NacosGracefulShutdownDelegate'
  'MacOSDnsServerAddressStreamProvider'
  'shutdown logs contain no unexpected ERROR lines'
  '--verify-stopped'
)

for contract in "${shutdown_contracts[@]}"; do
  grep -Fq -- "${contract}" "${shutdown_subject}" \
    || { printf 'Missing ordered-shutdown contract: %s\n' "${contract}" >&2; exit 1; }
done

if grep -Eq 'pkill|kill -9|SIGKILL' "${shutdown_subject}"; then
  printf 'Ordered shutdown helper contains a forbidden broad/forced stop.\n' >&2
  exit 1
fi

printf 'release-candidate contract tests: OK\n'
