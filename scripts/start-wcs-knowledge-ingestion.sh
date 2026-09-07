#!/usr/bin/env bash

set -euo pipefail

knowledge_base_id=""
data_source_id=""
region=""
wait_for_completion="false"
timeout_seconds=900
poll_seconds=10
min_indexed_documents=1

usage() {
  cat <<'EOF'
Usage:
  scripts/start-wcs-knowledge-ingestion.sh \
    --knowledge-base-id <id> \
    --data-source-id <id> [options]

Required:
  --knowledge-base-id <id>       WCS Bedrock Knowledge Base ID
  --data-source-id <id>          WCS S3 data source ID

Options:
  --region <region>              AWS region (optional; uses the AWS CLI chain otherwise)
  --wait                         Poll until ingestion completes or fails
  --timeout-seconds <seconds>    Maximum wait time (default: 900)
  --poll-seconds <seconds>       Poll interval (default: 10)
  --min-indexed-documents <n>    Minimum indexed documents when waiting (default: 1)
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
    --region)
      (($# >= 2)) || die "--region requires a value"
      region="$2"
      shift 2
      ;;
    --wait)
      wait_for_completion="true"
      shift
      ;;
    --timeout-seconds)
      (($# >= 2)) || die "--timeout-seconds requires a value"
      timeout_seconds="$2"
      shift 2
      ;;
    --poll-seconds)
      (($# >= 2)) || die "--poll-seconds requires a value"
      poll_seconds="$2"
      shift 2
      ;;
    --min-indexed-documents)
      (($# >= 2)) || die "--min-indexed-documents requires a value"
      min_indexed_documents="$2"
      shift 2
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
[[ "$timeout_seconds" =~ ^[1-9][0-9]*$ ]] || die "timeout must be a positive integer"
[[ "$poll_seconds" =~ ^[1-9][0-9]*$ ]] || die "poll interval must be a positive integer"
[[ "$min_indexed_documents" =~ ^[0-9]+$ ]] || die "minimum indexed documents must be a non-negative integer"

require_command aws
require_command jq

aws_cli=(aws)
if [[ -n "$region" ]]; then
  aws_cli+=(--region "$region")
fi

get_statistics() {
  "${aws_cli[@]}" bedrock-agent get-ingestion-job \
    --knowledge-base-id "$knowledge_base_id" \
    --data-source-id "$data_source_id" \
    --ingestion-job-id "$ingestion_job_id" \
    --query 'ingestionJob.statistics' \
    --output json
}

print_statistics() {
  local statistics="$1"
  local scanned indexed failed deleted
  scanned="$(jq -r '.numberOfDocumentsScanned // 0' <<<"$statistics")"
  indexed="$(jq -r '.numberOfDocumentsIndexed // 0' <<<"$statistics")"
  failed="$(jq -r '.numberOfDocumentsFailed // 0' <<<"$statistics")"
  deleted="$(jq -r '.numberOfDocumentsDeleted // 0' <<<"$statistics")"
  printf 'Ingestion statistics: scanned=%s indexed=%s failed=%s deleted=%s\n' \
    "$scanned" "$indexed" "$failed" "$deleted"
}

ingestion_job_id="$(
  "${aws_cli[@]}" bedrock-agent start-ingestion-job \
    --knowledge-base-id "$knowledge_base_id" \
    --data-source-id "$data_source_id" \
    --query 'ingestionJob.ingestionJobId' \
    --output text
)"

printf 'Started WCS Knowledge Base ingestion job: %s\n' "$ingestion_job_id"

if [[ "$wait_for_completion" != "true" ]]; then
  exit 0
fi

deadline=$((SECONDS + timeout_seconds))

while true; do
  status="$(
    "${aws_cli[@]}" bedrock-agent get-ingestion-job \
      --knowledge-base-id "$knowledge_base_id" \
      --data-source-id "$data_source_id" \
      --ingestion-job-id "$ingestion_job_id" \
      --query 'ingestionJob.status' \
      --output text
  )"
  printf 'Ingestion status: %s\n' "$status"

  case "$status" in
    COMPLETE)
      statistics="$(get_statistics)"
      print_statistics "$statistics"
      indexed="$(jq -r '.numberOfDocumentsIndexed // 0' <<<"$statistics")"
      failed="$(jq -r '.numberOfDocumentsFailed // 0' <<<"$statistics")"
      (( failed == 0 )) || die "ingestion completed with failed documents: $failed"
      (( indexed >= min_indexed_documents )) || die \
        "ingestion indexed $indexed documents; expected at least $min_indexed_documents"
      exit 0
      ;;
    FAILED|STOPPED)
      print_statistics "$(get_statistics)"
      exit 1
      ;;
    *)
      if (( SECONDS >= deadline )); then
        die "ingestion timed out after ${timeout_seconds}s: job=$ingestion_job_id"
      fi
      sleep "$poll_seconds"
      ;;
  esac
done
