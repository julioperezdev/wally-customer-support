package com.wally.customersupport.backoffice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPage;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPageRequest;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRunSummary;
import com.wally.customersupport.agent.application.registry.AgentRegistryActivationView;
import com.wally.customersupport.agent.application.registry.AgentRegistryAgentView;
import com.wally.customersupport.agent.application.registry.AgentRegistryVersionView;
import com.wally.customersupport.agent.application.service.AgentEvaluationHistoryQueryService;
import com.wally.customersupport.agent.application.service.AgentRegistryQueryService;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import com.wally.customersupport.backoffice.application.model.BackofficeAgentMapQuery;
import com.wally.customersupport.backoffice.application.model.BackofficeAgentMapSimulationRequest;
import org.junit.jupiter.api.Test;

class BackofficeAgentMapServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");

    @Test
    void projectsGraphAndAggregatedEvidenceWithoutExposingPromptContent() {
        AgentRegistryQueryService registry = mock(AgentRegistryQueryService.class);
        AgentEvaluationHistoryQueryService history = mock(AgentEvaluationHistoryQueryService.class);
        when(registry.search(any())).thenReturn(List.of(agent("catalog-specialist", 1, null)));
        when(history.search(any(), any())).thenReturn(new AgentEvaluationHistoryPage(
                List.of(new AgentEvaluationRunSummary(
                        UUID.randomUUID(), "catalog-v1", "catalog-specialist", "1", "bedrock", "model-v1",
                        NOW.minusSeconds(800), NOW.minusSeconds(700), 100, 2, 2, 0, 1, 0.9, Map.of(),
                        120L, 90L, new BigDecimal("0.001200"))),
                0, AgentEvaluationHistoryPageRequest.MAX_PAGE_SIZE, 1, 1));

        var result = new BackofficeAgentMapService(registry, history).describe(
                new BackofficeAgentMapQuery("prod", "telegram", "catalog-search", null));

        var node = result.useCases().getFirst().agents().getFirst();
        assertThat(node.agentId()).isEqualTo("catalog-specialist");
        assertThat(node.executionCount()).isEqualTo(1);
        assertThat(node.successRate()).isEqualTo(1);
        assertThat(node.totalTokens()).isEqualTo(120L);
        assertThat(node.estimatedCostUsd()).isEqualByComparingTo("0.001200");
        assertThat(result.useCases().getFirst().edges())
                .extracting(edge -> edge.relation())
                .contains("ROUTES_TO", "USES_TOOL", "USES_KNOWLEDGE_SOURCE", "HANDOFF_TO_HUMAN");
    }

    @Test
    void simulatesHumanHandoffWhenActiveAgentHasNoCompatibleFallback() {
        AgentRegistryQueryService registry = mock(AgentRegistryQueryService.class);
        AgentEvaluationHistoryQueryService history = mock(AgentEvaluationHistoryQueryService.class);
        when(registry.search(any())).thenReturn(List.of(agent("catalog-specialist", 1, null)));
        when(history.search(any(), any())).thenReturn(new AgentEvaluationHistoryPage(
                List.of(), 0, AgentEvaluationHistoryPageRequest.MAX_PAGE_SIZE, 0, 0));

        var result = new BackofficeAgentMapService(registry, history).simulate(
                new BackofficeAgentMapSimulationRequest("prod", "telegram", "catalog-search", "catalog-specialist", 1));

        assertThat(result.changed()).isTrue();
        assertThat(result.outcome()).isEqualTo("HUMAN_REQUIRED");
        assertThat(result.route()).extracting(step -> step.kind()).containsExactly("DISABLED", "HUMAN");
    }

    private static AgentRegistryAgentView agent(String agentId, int version, String fallbackAgentId) {
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        return new AgentRegistryAgentView(
                agentId,
                List.of(new AgentRegistryVersionView(
                        agentId, version, "Catalog specialist", "Searches catalog", AgentLifecycleState.APPROVED,
                        "bedrock", "model-v1", BigDecimal.ZERO, BigDecimal.ONE, "system-v1", hash,
                        "input-v1", "output-v1", Set.of("catalog.search"), Set.of("catalog-kb"),
                        "summary-v1", "response-v1", 10_000, 2, 2_000, 1_000,
                        new BigDecimal("0.050000"), fallbackAgentId, "eval-v1", NOW.minusSeconds(86_400), NOW)),
                List.of(new AgentRegistryActivationView(
                        agentId, version, "prod", "telegram", "catalog-search", "rollout", 100,
                        true, false, null, NOW)));
    }
}
