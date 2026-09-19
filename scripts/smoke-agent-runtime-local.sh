#!/usr/bin/env bash

set -euo pipefail

echo "Running WCS local agent runtime smoke tests"
echo "No AWS credentials, remote AppConfig, Telegram or external services are used"

mvn -B -q -Dtest=\
WallyCustomerSupportApplicationIntegrationTest,\
AgentActivationCommandServiceTest,\
AgentActivationResolverTest,\
AgentRuntimeDefinitionResolverTest,\
AgentSpecialistRegistryTest,\
AgentShadowRuntimeServiceTest,\
FeatureFlagRuntimeServiceTest,\
ConversationOrchestratorTest,\
CatalogSpecialistExecutorTest,\
AgentRegistryPersistenceIntegrationTest,\
ConversationStateToolTest,\
CartManageToolTest,\
CheckoutCreateToolTest,\
HumanHandoffToolTest \
test

echo "Local agent runtime smoke completed: activation, rollback and safe fallback passed"
