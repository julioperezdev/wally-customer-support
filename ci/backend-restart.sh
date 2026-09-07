#!/usr/bin/env bash
set -Eeuo pipefail

: "${AWS_REGION:?AWS_REGION is required}"
: "${BACKEND_APPRUNNER_SERVICE_ARN:?BACKEND_APPRUNNER_SERVICE_ARN is required}"

readonly SERVICE_ARN="${BACKEND_APPRUNNER_SERVICE_ARN}"
readonly TIMEOUT_SECONDS="${APP_RUNNER_RESTART_TIMEOUT_SECONDS:-1200}"

log() {
  printf '[backend-restart] %s\n' "$*"
}

fail() {
  printf '::error::[backend-restart] %s\n' "$*" >&2
  exit 1
}

describe_service_status() {
  aws apprunner describe-service \
    --service-arn "$SERVICE_ARN" \
    --query 'Service.Status' \
    --output text
}

wait_for_operation() {
  local operation_id="$1"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))

  while (( SECONDS < deadline )); do
    local status
    status="$(aws apprunner list-operations \
      --service-arn "$SERVICE_ARN" \
      --query "OperationSummaryList[?Id=='$operation_id'].Status | [0]" \
      --output text)"

    log "App Runner restart operation=$operation_id status=$status"
    case "$status" in
      SUCCEEDED)
        return 0
        ;;
      FAILED|ERROR|ROLLBACK_IN_PROGRESS|ROLLBACK_SUCCEEDED|ROLLBACK_FAILED)
        fail "App Runner restart failed: operation=$operation_id status=$status"
        ;;
      ""|None)
        fail "App Runner restart operation was not found: operation=$operation_id"
        ;;
    esac

    sleep 10
  done

  fail "App Runner restart timed out after ${TIMEOUT_SECONDS}s: operation=$operation_id"
}

wait_for_running_service() {
  local deadline=$((SECONDS + TIMEOUT_SECONDS))

  while (( SECONDS < deadline )); do
    local status
    status="$(describe_service_status)"
    log "App Runner service status=$status"

    case "$status" in
      RUNNING)
        return 0
        ;;
      CREATE_FAILED|DELETE_FAILED|DELETED|FAILED|PAUSED|PAUSING)
        fail "App Runner service cannot become RUNNING from status=$status"
        ;;
    esac

    sleep 10
  done

  fail "App Runner service did not become RUNNING within ${TIMEOUT_SECONDS}s"
}

service_status="$(describe_service_status)"
log "Preparing AppConfig reload for service=$SERVICE_ARN status=$service_status"

[ "$service_status" = "RUNNING" ] || {
  fail "App Runner service must be RUNNING before restart; status=$service_status"
}

operation_id="$(aws apprunner start-deployment \
  --service-arn "$SERVICE_ARN" \
  --query 'OperationId' \
  --output text)"
[ -n "$operation_id" ] && [ "$operation_id" != "None" ] || {
  fail "App Runner did not return a restart operation id"
}

wait_for_operation "$operation_id"
wait_for_running_service

service_url="$(aws apprunner describe-service \
  --service-arn "$SERVICE_ARN" \
  --query 'Service.ServiceUrl' \
  --output text)"
[ -n "$service_url" ] && [ "$service_url" != "None" ] || {
  fail "App Runner did not return a service URL"
}

health_url="https://${service_url}/actuator/health"
log "Checking restarted backend health"
curl --fail --silent --show-error --retry 10 --retry-delay 5 --max-time 15 "$health_url"
printf '\n'

log "Backend restart succeeded; latest deployed AppConfig is now available to the application bootstrap"
