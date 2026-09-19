package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.conversation.application.tool.WcsToolContractCatalog;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionPlan;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionStep;
import org.junit.jupiter.api.Test;

class AgentSpecialistRegistryTest {

    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void registersAllCoreSpecialistsWithBoundedResponsibilities() {
        AgentSpecialistRegistry registry = AgentSpecialistRegistry.defaultRegistry();

        assertThat(registry.all())
                .extracting(AgentSpecialistDefinition::agentId)
                .containsExactlyInAnyOrder(
                        "conversation-router",
                        "catalog-specialist",
                        "conversation-state",
                        "knowledge-specialist",
                        "checkout-specialist",
                        "support-safety",
                        "response-humanizer");
        assertThat(registry.find("catalog-specialist").orElseThrow().allowedTools())
                .containsExactlyInAnyOrder("catalog.search", "catalog.stock");
    }

    @Test
    void allowsCatalogSpecialistToExecuteOnlyItsVersionedTool() {
        AgentSpecialistRegistry registry = AgentSpecialistRegistry.defaultRegistry();
        AgentRuntimeDefinition definition = definition(
                "catalog-specialist",
                Set.of(WcsToolContractCatalog.CATALOG_SEARCH));
        ConversationExecutionPlan plan = new ConversationExecutionPlan(
                ConversationExecutionPlan.CURRENT_WORKFLOW_VERSION,
                "CATALOG_SEARCH",
                com.wally.customersupport.conversation.domain.model.ConversationExecutionAction.CATALOG_SEARCH,
                java.util.List.of(new ConversationExecutionStep(
                        "catalog-search",
                        "catalog-specialist",
                        "catalog-query",
                        "catalog.search",
                        "catalog-input-v1",
                        "catalog-output-v1")),
                3,
                false,
                null);

        assertThat(registry.validateExecution(definition, plan).valid()).isTrue();
    }

    @Test
    void rejectsAPlanToolOutsideTheActiveAgentAllowlist() {
        AgentSpecialistRegistry registry = AgentSpecialistRegistry.defaultRegistry();
        AgentRuntimeDefinition definition = definition("catalog-specialist", Set.of());
        ConversationExecutionPlan plan = new ConversationExecutionPlan(
                ConversationExecutionPlan.CURRENT_WORKFLOW_VERSION,
                "CATALOG_SEARCH",
                com.wally.customersupport.conversation.domain.model.ConversationExecutionAction.CATALOG_SEARCH,
                java.util.List.of(new ConversationExecutionStep(
                        "catalog-search",
                        "catalog-specialist",
                        "catalog-query",
                        "catalog.search",
                        "catalog-input-v1",
                        "catalog-output-v1")),
                3,
                false,
                null);

        assertThat(registry.validateExecution(definition, plan).reason())
                .isEqualTo("TOOL_NOT_ALLOWED");
    }

    @Test
    void rejectsSchemaMismatchBeforeToolExecution() {
        AgentSpecialistRegistry registry = AgentSpecialistRegistry.defaultRegistry();
        AgentRuntimeDefinition definition = definition(
                "catalog-specialist",
                Set.of(WcsToolContractCatalog.CATALOG_SEARCH));
        ConversationExecutionPlan plan = new ConversationExecutionPlan(
                ConversationExecutionPlan.CURRENT_WORKFLOW_VERSION,
                "CATALOG_SEARCH",
                com.wally.customersupport.conversation.domain.model.ConversationExecutionAction.CATALOG_SEARCH,
                java.util.List.of(new ConversationExecutionStep(
                        "catalog-search",
                        "catalog-specialist",
                        "catalog-query",
                        "catalog.search",
                        "catalog-input-v0",
                        "catalog-output-v1")),
                3,
                false,
                null);

        assertThat(registry.validateExecution(definition, plan).reason())
                .isEqualTo("TOOL_SCHEMA_MISMATCH");
    }

    private static AgentRuntimeDefinition definition(String agentId, Set<String> allowedTools) {
        return new AgentRuntimeDefinition(
                agentId,
                1,
                agentId,
                "Specialist",
                "wcs",
                "deterministic-v1",
                AgentInferenceParameters.deterministic(),
                "system-v1",
                HASH,
                "input-v1",
                "output-v1",
                allowedTools,
                Set.of(),
                "conversation-summary-v1",
                "grounded-customer-support-v1",
                Duration.ofSeconds(10),
                2,
                2_000,
                1_000,
                BigDecimal.ZERO,
                "support-safety",
                "specialist-v1");
    }
}
