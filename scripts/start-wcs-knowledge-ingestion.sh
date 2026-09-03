#!/usr/bin/env bash

set -euo pipefail

knowledge_base_id=""
data_source_id=""
wait_for_completion="false"

usage() {
  cat <<'EOF'
Usage:
  scripts/start-wcs-knowledge-ingestion.sh \
    --knowledge-base-id <id> \
    --data-source-id <id> [--wait]

Required:
  --knowledge-base-id <id>       WCS Bedrock Knowledge Base ID
  --data-source-id <id>          WCS S3 data source ID

Options:
  --wait                         Poll until ingestion completes or fails
  -h, --help                     Show this help

The command uses the AWS CLI credential chain already configured on the
operator machine or supplied by CI. It does not read or print application
secrets.
EOF
}

die() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

while (($# > 0)); do
  case "$1" in
    --knowledge-base-id)
      (($# >= 2)) || die "--knowledge-base-id requires a value"
      knowledge_base_id="$2"
      shift 2
      ;;
    --data-source-id)
      (($# >= 2)) || die "--data-source-id requires a value"
      data_source_id="$2"
      shift 2
      ;;
    --wait)
      wait_for_completion="true"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "unknown option: $1"
      ;;
  esac
done

[[ -n "$knowledge_base_id" ]] || die "knowledge base ID cannot be empty"
[[ -n "$data_source_id" ]] || die "data source ID cannot be empty"

require_command aws

ingestion_job_id="$(
  aws bedrock-agent start-ingestion-job \
    --knowledge-base-id "$knowledge_base_id" \
    --data-source-id "$data_source_id" \
    --query 'ingestionJob.ingestionJobId' \
    --output text
)"

printf 'Started WCS Knowledge Base ingestion job: %s\n' "$ingestion_job_id"

if [[ "$wait_for_completion" != "true" ]]; then
  exit 0
fi

while true; do
  status="$(
    aws bedrock-agent get-ingestion-job \
      --knowledge-base-id "$knowledge_base_id" \
      --data-source-id "$data_source_id" \
      --ingestion-job-id "$ingestion_job_id" \
      --query 'ingestionJob.status' \
      --output text
  )"
  printf 'Ingestion status: %s\n' "$status"

  case "$status" in
    COMPLETE)
      exit 0
      ;;
    FAILED|STOPPED)
      exit 1
      ;;
    *)
      sleep 5
      ;;
  esac
done
