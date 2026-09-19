#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "== WCS final local gate =="
echo "Repository: $ROOT_DIR"
echo "No deploy, Terraform apply, AppConfig mutation or remote service call is performed."

echo
echo "[1/7] Maven verify"
mvn -B verify

echo
echo "[2/7] Runtime activation, kill switch, rollback and safe fallback smoke"
./scripts/smoke-agent-runtime-local.sh

echo
echo "[3/7] Offline evaluation"
mvn -B \
  -Dtest=AgentEvaluationRunnerTest,AgentEvaluationApplicationServiceTest,AgentEvaluationQualityScorecardTest,ResponsePolicyEvaluatorTest \
  test

echo
echo "[4/7] Complete conversational sequences and Testcontainers integration"
mvn -B \
  -Dtest=ConversationOrchestratorTest,WallyCustomerSupportApplicationIntegrationTest,ConversationIntentV4FixtureTest \
  test

echo
echo "[5/7] Dashboard and observability configuration"
command -v jq >/dev/null 2>&1 || {
  echo "jq is required to validate the provisioned Grafana dashboard" >&2
  exit 1
}
jq empty observability/grafana/provisioning/dashboards/wcs-observability.json

if command -v docker >/dev/null 2>&1; then
  docker compose \
    --env-file observability/grafana/.env.example \
    -f observability/grafana/docker-compose.yml \
    config --quiet
else
  echo "Docker is required for Grafana Compose validation" >&2
  exit 1
fi

echo
echo "[6/7] Flyway migration continuity"
previous_version=0
migration_count=0
while IFS= read -r version; do
  [[ -n "$version" ]] || continue
  expected_version=$((previous_version + 1))
  if [[ "$version" -ne "$expected_version" ]]; then
    echo "Migration gap detected: expected V${expected_version}, found V${version}" >&2
    exit 1
  fi
  previous_version="$version"
  migration_count=$((migration_count + 1))
done < <(
  find src/main/resources/db/migration -maxdepth 1 -type f -name 'V*.sql' \
    -print | sed -E 's#^.*/V([0-9]+)__.*#\1#' | sort -n
)

if [[ "$migration_count" -eq 0 ]]; then
  echo "No Flyway migrations found" >&2
  exit 1
fi
echo "Flyway migrations are contiguous: V1..V${previous_version}"

echo
echo "[7/7] Gate result"
echo "PASS: local code, Testcontainers, conversational sequences, offline evaluation, dashboards and migrations"
echo "NOTE: real Bedrock is optional and is not invoked by this script; use the explicit real-provider smoke only when cost and AWS credentials are intended."
