#!/usr/bin/env bash

set -euo pipefail

bucket_name="${1:-}"
region="${2:-${AWS_REGION:-us-east-1}}"

if [[ -z "$bucket_name" ]]; then
  echo "::error::An S3 bucket name is required" >&2
  exit 2
fi

set +e
head_bucket_error="$(aws s3api head-bucket \
  --bucket "$bucket_name" \
  --region "$region" 2>&1)"
head_bucket_status=$?
set -e

if [[ "$head_bucket_status" -eq 0 ]]; then
  echo "The configured AWS principal can read S3 bucket '$bucket_name'."
  exit 0
fi

if [[ "$head_bucket_error" == *"(404)"* || "$head_bucket_error" == *"Not Found"* || "$head_bucket_error" == *"NoSuchBucket"* ]]; then
  echo "S3 bucket '$bucket_name' does not exist yet; Terraform may create it."
  exit 0
fi

echo "::error::The configured AWS principal cannot read S3 bucket '$bucket_name' before planning." >&2
echo "::error::Run the documented one-time IAM bootstrap, then rerun the Terraform plan." >&2
printf '%s\n' "$head_bucket_error" >&2
exit 1
