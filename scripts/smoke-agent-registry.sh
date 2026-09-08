#!/usr/bin/env bash
set -euo pipefail

endpoint="${WCS_AGENT_REGISTRY_URL:-http://localhost:8080/internal/agent-registry/agents}"
agent_id="${WCS_AGENT_ID:-catalog-specialist}"
limit="${WCS_AGENT_REGISTRY_LIMIT:-10}"

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  echo "Usage: $0 [--url HTTPS_ENDPOINT]"
  echo "Environment: WCS_AGENT_REGISTRY_URL, WCS_AGENT_ID, WCS_AGENT_REGISTRY_LIMIT, WCS_CONTROL_PLANE_TOKEN"
  exit 0
fi

if [[ "${1:-}" == "--url" ]]; then
  if [[ -z "${2:-}" ]]; then
    echo "Usage: $0 [--url HTTPS_ENDPOINT]" >&2
    exit 2
  fi
  endpoint="$2"
fi

separator="?"
if [[ "$endpoint" == *\?* ]]; then
  separator="&"
fi

curl_args=(--fail-with-body --silent --show-error \
  -H "Accept: application/json")
if [[ -n "${WCS_CONTROL_PLANE_TOKEN:-}" ]]; then
  curl_args+=(-H "Authorization: Bearer ${WCS_CONTROL_PLANE_TOKEN}")
fi

curl "${curl_args[@]}" \
  "${endpoint}${separator}agentId=${agent_id}&limit=${limit}"
printf '\n'
