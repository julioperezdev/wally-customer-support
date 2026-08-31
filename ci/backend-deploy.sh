#!/usr/bin/env bash
set -Eeuo pipefail

: "${AWS_REGION:?AWS_REGION is required}"
: "${BACKEND_APPRUNNER_SERVICE_ARN:?BACKEND_APPRUNNER_SERVICE_ARN is required}"
: "${COMMIT_SHA:?COMMIT_SHA is required}"
: "${IMAGE_REFERENCE:?IMAGE_REFERENCE is required}"

readonly SERVICE_ARN="${BACKEND_APPRUNNER_SERVICE_ARN}"
readonly TIMEOUT_SECONDS="${APP_RUNNER_TIMEOUT_SECONDS:-1200}"

log() {
  printf '[backend-deploy] %s\n' "$*"
}

fail() {
  printf '::error::[backend-deploy] %s\n' "$*" >&2
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
  local description="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))

  while (( SECONDS < deadline )); do
    local status
    status="$(aws apprunner list-operations \
      --service-arn "$SERVICE_ARN" \
      --query "OperationSummaryList[?Id=='$operation_id'].Status | [0]" \
      --output text)"

    log "$description: operation=$operation_id status=$status"
    case "$status" in
      SUCCEEDED)
        return 0
        ;;
      FAILED|ERROR|ROLLBACK_FAILED)
        fail "$description failed: operation=$operation_id status=$status"
        ;;
    esac

    sleep 10
  done

  fail "$description timed out after ${TIMEOUT_SECONDS}s: operation=$operation_id"
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
log "Preparing commit=$COMMIT_SHA service=$SERVICE_ARN status=$service_status"

case "$service_status" in
  PAUSED)
    resume_operation="$(aws apprunner resume-service \
      --service-arn "$SERVICE_ARN" \
      --query 'OperationId' \
      --output text)"
    wait_for_operation "$resume_operation" "Resume App Runner service"
    wait_for_running_service
    ;;
  RUNNING)
    ;;
  *)
    fail "App Runner service must be RUNNING or PAUSED before deployment; status=$service_status"
    ;;
esac

source_configuration="$(aws apprunner describe-service \
  --service-arn "$SERVICE_ARN" \
  --query 'Service.SourceConfiguration' \
  --output json)"
previous_image="$(jq -r '.ImageRepository.ImageIdentifier // empty' <<<"$source_configuration")"
updated_source_configuration="$(jq --arg image "$IMAGE_REFERENCE" \
  '.ImageRepository.ImageIdentifier = $image | .AutoDeploymentsEnabled = false' \
  <<<"$source_configuration")"

log "Pinning App Runner image from ${previous_image:-unknown} to immutable digest"
update_operation="$(aws apprunner update-service \
  --service-arn "$SERVICE_ARN" \
  --source-configuration "$updated_source_configuration" \
  --query 'OperationId' \
  --output text)"
wait_for_operation "$update_operation" "Pin image"
wait_for_running_service

deployment_operation="$(aws apprunner start-deployment \
  --service-arn "$SERVICE_ARN" \
  --query 'OperationId' \
  --output text)"
wait_for_operation "$deployment_operation" "Deploy image"
wait_for_running_service

service_url="$(aws apprunner describe-service \
  --service-arn "$SERVICE_ARN" \
  --query 'Service.ServiceUrl' \
  --output text)"
health_url="https://${service_url}/actuator/health"
log "Checking deployed backend health"
curl --fail --silent --show-error --retry 10 --retry-delay 5 --max-time 15 "$health_url"
printf '\n'

log "Backend deployment succeeded: commit=$COMMIT_SHA"
