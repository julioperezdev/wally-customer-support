#!/usr/bin/env bash

set -euo pipefail

echo "Running the synthetic, no-secret shadow boundary smoke tests"
mvn -q -Dtest=AgentShadowRuntimeServiceTest,AgentShadowQualityGateTest test
echo "Shadow smoke completed: active response remains authoritative"
