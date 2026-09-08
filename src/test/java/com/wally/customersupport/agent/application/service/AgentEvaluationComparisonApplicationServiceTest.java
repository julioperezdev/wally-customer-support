package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRun;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationRunRepository;
import com.wally.customersupport.agent.domain.model.AgentEvaluationExecutionMetadata;
import com.wally.customersupport.agent.domain.model.AgentEvaluationResult;
import com.wally.customersupport.agent.domain.model.AgentEvaluationSuiteResult;
import org.junit.jupiter.api.Test;

class AgentEvaluationComparisonApplicationServiceTest {

    private static final UUID BASELINE_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID CANDIDATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");

    @Test
    void comparesQualityAndAvailableOperationalMetricsWithoutResponseContent() {
        AgentEvaluationRunRepository repository = mock(AgentEvaluationRunRepository.class);
        when(repository.findById(BASELINE_ID)).thenReturn(Optional.of(run(
                BASELINE_ID, "catalog-response-v1", 10, 0.7, metadata(100, 20, "0.10"))));
        when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.of(run(
                CANDIDATE_ID, "catalog-response-v1", 15, 0.9, metadata(150, 25, "0.15"))));

        var comparison = new AgentEvaluationComparisonApplicationService(repository)
                .compare(BASELINE_ID, CANDIDATE_ID)
                .orElseThrow();

        assertThat(comparison.datasetVersion()).isEqualTo("catalog-response-v1");
        assertThat(comparison.baseline().agentVersion()).isEqualTo("v1");
        assertThat(comparison.candidate().runId()).isEqualTo(CANDIDATE_ID);
        assertThat(comparison.metricDelta().averageScoreDelta()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(comparison.metricDelta().durationMsDelta()).isEqualTo(5);
        assertThat(comparison.metricDelta().totalTokensDelta().orElseThrow()).isEqualTo(50);
        assertThat(comparison.metricDelta().providerLatencyMsDelta().orElseThrow()).isEqualTo(5);
        assertThat(comparison.metricDelta().estimatedCostUsdDelta()).contains(BigDecimal.valueOf(0.05));
        assertThat(comparison.scenarios()).singleElement()
                .satisfies(scenario -> assertThat(scenario.scoreDelta())
                        .isCloseTo(0.2, org.assertj.core.data.Offset.offset(0.000001)));
        assertThat(comparison.toString()).doesNotContain("respuesta de prueba");
    }

    @Test
    void marksOperationalDeltasUnavailableWhenMetadataIsMissing() {
        AgentEvaluationRunRepository repository = mock(AgentEvaluationRunRepository.class);
        when(repository.findById(BASELINE_ID)).thenReturn(Optional.of(
                run(BASELINE_ID, "catalog-response-v1", 10, 0.7, null)));
        when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.of(
                run(CANDIDATE_ID, "catalog-response-v1", 15, 0.9, metadata(150, 25, "0.15"))));

        var delta = new AgentEvaluationComparisonApplicationService(repository)
                .compare(BASELINE_ID, CANDIDATE_ID)
                .orElseThrow()
                .metricDelta();

        assertThat(delta.totalTokensDelta()).isEmpty();
        assertThat(delta.providerLatencyMsDelta()).isEmpty();
        assertThat(delta.estimatedCostUsdDelta()).isEmpty();
    }

    @Test
    void returnsEmptyForAMissingRunAndRejectsDifferentDatasets() {
        AgentEvaluationRunRepository repository = mock(AgentEvaluationRunRepository.class);
        when(repository.findById(BASELINE_ID)).thenReturn(Optional.empty());
        when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.of(
                run(CANDIDATE_ID, "catalog-response-v1", 15, 0.9, null)));

        assertThat(new AgentEvaluationComparisonApplicationService(repository)
                .compare(BASELINE_ID, CANDIDATE_ID)).isEmpty();

        when(repository.findById(BASELINE_ID)).thenReturn(Optional.of(
                run(BASELINE_ID, "catalog-response-v1", 10, 0.7, null)));
        when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.of(
                run(CANDIDATE_ID, "other-dataset", 15, 0.9, null)));

        assertThatThrownBy(() -> new AgentEvaluationComparisonApplicationService(repository)
                .compare(BASELINE_ID, CANDIDATE_ID))
                .isInstanceOf(IncompatibleEvaluationDatasetException.class)
                .hasMessage("evaluation runs must use the same dataset");
    }

    private static AgentEvaluationRun run(
            UUID runId,
            String datasetVersion,
            long durationMs,
            double score,
            AgentEvaluationExecutionMetadata metadata) {
        AgentEvaluationResult scenario = new AgentEvaluationResult(
                "scenario-1", datasetVersion, true, score, List.of(), metadata);
        return new AgentEvaluationRun(
                runId,
                datasetVersion,
                "catalog-specialist",
                "v1",
                "mock",
                "deterministic-v1",
                Instant.parse("2026-09-08T00:00:00Z"),
                Instant.parse("2026-09-08T00:00:01Z"),
                durationMs,
                new AgentEvaluationSuiteResult(
                        datasetVersion,
                        List.of(scenario),
                        1,
                        1,
                        0,
                        1.0,
                        score,
                        Map.of()));
    }

    private static AgentEvaluationExecutionMetadata metadata(
            int totalTokens,
            long providerLatencyMs,
            String cost) {
        return new AgentEvaluationExecutionMetadata(
                "catalog-specialist",
                "v1",
                "mock",
                "deterministic-v1",
                10,
                providerLatencyMs,
                null,
                null,
                totalTokens,
                new BigDecimal(cost),
                "test-pricing-v1");
    }
}
