#!/usr/bin/env bash

set -euo pipefail

readonly ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
readonly POSTGRES_CONTAINER="${WCS_LOCAL_POSTGRES_CONTAINER:-wcs-postgres}"
readonly JAR_PATH="${WCS_LOCAL_JAR_PATH:-$ROOT_DIR/target/wally-customer-support-0.0.1-SNAPSHOT.jar}"

die() {
  printf 'Error: %s\n' "$*" >&2
  exit 1
}

command -v docker >/dev/null 2>&1 || die "Docker is required"
command -v java >/dev/null 2>&1 || die "Java is required"
[[ -f "$JAR_PATH" ]] || die "Build the application first: mvn -B -DskipTests package"
docker inspect "$POSTGRES_CONTAINER" >/dev/null 2>&1 \
  || die "PostgreSQL container '$POSTGRES_CONTAINER' is not running"

local_db_password="$(docker inspect "$POSTGRES_CONTAINER" \
  --format '{{range .Config.Env}}{{println .}}{{end}}' \
  | sed -n 's/^POSTGRES_PASSWORD=//p')"
[[ -n "$local_db_password" ]] || die "POSTGRES_PASSWORD is not available in the local container"

printf '%s\n' "Starting WCS locally with SQL agent profiles and real Bedrock"
printf '%s\n' "PostgreSQL: $POSTGRES_CONTAINER · port: ${SERVER_PORT:-18080}"
printf '%s\n' "AWS credentials are resolved by the SDK chain; no credential is stored or printed."

cd "$ROOT_DIR"
exec env \
  SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:5432/wcs}" \
  SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-wcs}" \
  SPRING_DATASOURCE_PASSWORD="$local_db_password" \
  WCS_EXTERNAL_CONFIG_APPCONFIG_ENABLED=false \
  WCS_EXTERNAL_CONFIG_SECRETS_MANAGER_ENABLED=false \
  WCS_AI_PROVIDER=bedrock \
  WCS_AI_MODEL="${WCS_AI_MODEL:-openai.gpt-oss-20b-1:0}" \
  WCS_AI_REGION="${WCS_AI_REGION:-us-east-1}" \
  WCS_AGENT_RUNTIME_ACTIVATION_ENABLED=true \
  WCS_AGENT_RUNTIME_ENVIRONMENT="${WCS_AGENT_RUNTIME_ENVIRONMENT:-prod}" \
  WCS_RAG_PROVIDER="${WCS_RAG_PROVIDER:-mock}" \
  WCS_TELEGRAM_ENABLED=true \
  WCS_TELEGRAM_ADAPTER=mock \
  WCS_TELEGRAM_WEBHOOK_SECRET_TOKEN="${WCS_TELEGRAM_WEBHOOK_SECRET_TOKEN:-local-agent-runtime-smoke-secret}" \
  WCS_INBOUND_POLL_INTERVAL_MS="${WCS_INBOUND_POLL_INTERVAL_MS:-500}" \
  AWS_REGION="${AWS_REGION:-us-east-1}" \
  AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-us-east-1}" \
  AWS_SDK_LOAD_CONFIG="${AWS_SDK_LOAD_CONFIG:-true}" \
  SERVER_PORT="${SERVER_PORT:-18080}" \
  java -jar "$JAR_PATH"
