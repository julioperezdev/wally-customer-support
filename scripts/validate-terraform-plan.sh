#!/usr/bin/env bash

set -euo pipefail

plan_json="${1:-}"

if [[ -z "$plan_json" || ! -s "$plan_json" ]]; then
  echo "::error::A non-empty Terraform plan JSON path is required" >&2
  exit 2
fi

inline_policy_over_limit="$({
  jq -r '
    .resource_changes[]?
    | select((.change.actions | index("delete")) == null)
    | select(
        .type == "aws_iam_group_policy"
        or .type == "aws_iam_role_policy"
        or .type == "aws_iam_user_policy"
      )
    | (.change.after.policy // null) as $policy
    | select($policy != null and ($policy | length) > 10240)
    | "\(.address): \($policy | length) bytes (limit 10240)"
  ' "$plan_json"
} || {
  echo "::error::Unable to inspect IAM inline policies in the Terraform plan" >&2
  exit 2
})"

managed_policy_over_limit="$({
  jq -r '
    .resource_changes[]?
    | select((.change.actions | index("delete")) == null)
    | select(.type == "aws_iam_policy")
    | (.change.after.policy // null) as $policy
    | select($policy != null and ($policy | length) > 6144)
    | "\(.address): \($policy | length) bytes (limit 6144)"
  ' "$plan_json"
} || {
  echo "::error::Unable to inspect IAM managed policies in the Terraform plan" >&2
  exit 2
})"

if [[ -n "$inline_policy_over_limit" || -n "$managed_policy_over_limit" ]]; then
  echo "::error::Terraform plan contains an IAM policy above the AWS size limit" >&2
  [[ -n "$inline_policy_over_limit" ]] && printf '%s\n' "$inline_policy_over_limit" >&2
  [[ -n "$managed_policy_over_limit" ]] && printf '%s\n' "$managed_policy_over_limit" >&2
  exit 1
fi

planned_bucket_creates="$({
  jq -r '
    .resource_changes[]?
    | select(.type == "aws_s3_bucket")
    | select((.change.actions | length) == 1 and (.change.actions[0] == "create"))
    | .change.after.bucket // empty
  ' "$plan_json"
} || {
  echo "::error::Unable to inspect S3 bucket creates in the Terraform plan" >&2
  exit 2
})"

while IFS= read -r bucket_name; do
  [[ -z "$bucket_name" ]] && continue

  if aws s3api head-bucket \
    --bucket "$bucket_name" \
    --region "${AWS_REGION:-us-east-1}" \
    >/dev/null 2>&1; then
    echo "::error::Terraform plans to create existing S3 bucket '$bucket_name'. Import it into the state before apply." >&2
    exit 1
  fi
done <<< "$planned_bucket_creates"

echo "Terraform plan safety checks passed: IAM policy sizes and existing S3 bucket creates are safe."
