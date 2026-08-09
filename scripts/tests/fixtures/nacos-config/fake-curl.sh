#!/usr/bin/env bash

set -euo pipefail

mode="${FAKE_NACOS_MODE:-missing-service}"
endpoint=""
service_name=""
write_out=false
fail_with_body=false

for argument in "$@"; do
  case "${argument}" in
    --write-out)
      write_out=true
      ;;
    --fail-with-body)
      fail_with_body=true
      ;;
    serviceName=*)
      service_name="${argument#serviceName=}"
      ;;
    */v3/admin/core/state/readiness)
      endpoint="server-readiness"
      ;;
    */v3/console/health/readiness)
      endpoint="console-readiness"
      ;;
    */v3/admin/ns/instance/list)
      endpoint="instance-list"
      ;;
  esac
done

emit_response() {
  local body="$1"
  local http_status="$2"

  if [[ "${write_out}" == true ]]; then
    printf '%s\n%s' "${body}" "${http_status}"
  else
    printf '%s' "${body}"
  fi

  if [[ "${fail_with_body}" == true && "${http_status}" -ge 400 ]]; then
    exit 22
  fi
}

case "${endpoint}" in
  server-readiness|console-readiness)
    emit_response '{"code":0,"message":"success","data":"ok"}' 200
    ;;
  instance-list)
    case "${mode}:${service_name}" in
      missing-service:worthit-gateway)
        emit_response \
          '{"code":30000,"message":"server error","data":"service WORTHIT_LOCAL@@worthit-gateway is not found!"}' \
          404
        ;;
      unexpected-404:worthit-gateway)
        emit_response \
          '{"code":30000,"message":"server error","data":"another resource is not found!"}' \
          404
        ;;
      server-error:worthit-gateway)
        emit_response \
          '{"code":30000,"message":"server error","data":"unexpected failure"}' \
          500
        ;;
      transport-error:worthit-gateway)
        printf 'simulated connection reset\n' >&2
        exit 7
        ;;
      malformed-success:worthit-gateway)
        emit_response '{"code":0,"message":"success","data":{}}' 200
        ;;
      *:worthit-auth)
        emit_response '{"code":0,"message":"success","data":[{},{}]}' 200
        ;;
      *:worthit-tracking)
        emit_response '{"code":0,"message":"success","data":[{}]}' 200
        ;;
      *:worthit-reminder)
        emit_response '{"code":0,"message":"success","data":[]}' 200
        ;;
      *:worthit-gateway)
        emit_response '{"code":0,"message":"success","data":[{}]}' 200
        ;;
      *)
        printf 'Fake curl received an unknown service: %s\n' \
          "${service_name}" >&2
        exit 64
        ;;
    esac
    ;;
  *)
    printf 'Fake curl received an unknown endpoint.\n' >&2
    exit 64
    ;;
esac
