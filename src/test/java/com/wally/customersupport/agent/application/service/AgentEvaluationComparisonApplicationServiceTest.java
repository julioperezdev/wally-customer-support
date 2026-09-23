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
import java.util.OptionalLong;
import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRun;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationEvidenceExport;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationComparison;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationComparisonAssessment;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationMetricDelta;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationQualityMetricDelta;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRunSummary;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationScenarioComparison;
import com.wally.customersupport.agent.application.evaluation.EvaluationEvidenceExportLimitException;
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
        assertThat(comparison.qualityDelta().utilityRateDelta())
                .isCloseTo(0.2, org.assertj.core.data.Offset.offset(0.000001));
        assertThat(comparison.assessment().outcome())
                .isEqualTo(AgentEvaluationComparisonAssessment.Outcome.QUALITY_IMPROVED);
        assertThat(comparison.assessment().improvedScenarioCount()).isEqualTo(1);
        assertThat(comparison.assessment().evidenceLevel())
                .isEqualTo(AgentEvaluationComparisonAssessment.EvidenceLevel.DESCRIPTIVE_NOT_STATISTICALLY_SIGNIFICANT);
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
                .isInstanceOf(IncompatibleEvaluationRunsException.class)
                .hasMessage("evaluation runs must use the same dataset");
    }

    @Test
    void exportsOnlyTheVersionedSanitizedComparison() {
        AgentEvaluationRunRepository repository = mock(AgentEvaluationRunRepository.class);
        when(repository.findById(BASELINE_ID)).thenReturn(Optional.of(
                run(BASELINE_ID, "catalog-response-v1", 10, 0.7, null)));
        when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.of(
                run(CANDIDATE_ID, "catalog-response-v1", 15, 0.9, null)));

        var comparisonService = new AgentEvaluationComparisonApplicationService(repository);
        var export = new AgentEvaluationEvidenceExportApplicationService(comparisonService)
                .export(BASELINE_ID, CANDIDATE_ID)
                .orElseThrow();

        assertThat(export.schemaVersion()).isEqualTo(AgentEvaluationEvidenceExport.SCHEMA_VERSION);
        assertThat(export.comparison().baselineRunId()).isEqualTo(BASELINE_ID);
        assertThat(export.comparison().candidateRunId()).isEqualTo(CANDIDATE_ID);
        assertThat(export.comparison().metricDelta().totalTokensDelta()).isEmpty();
        assertThat(export.toString()).doesNotContain("respuesta de prueba");
    }

    @Test
    void preservesMissingRunAndIncompatibleDatasetOutcomes() {
        AgentEvaluationRunRepository repository = mock(AgentEvaluationRunRepository.class);
        var comparisonService = new AgentEvaluationComparisonApplicationService(repository);
        var exportService = new AgentEvaluationEvidenceExportApplicationService(comparisonService);

        when(repository.findById(BASELINE_ID)).thenReturn(Optional.empty());
        when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.empty());
        assertThat(exportService.export(BASELINE_ID, CANDIDATE_ID)).isEmpty();

        when(repository.findById(BASELINE_ID)).thenReturn(Optional.of(
                run(BASELINE_ID, "catalog-response-v1", 10, 0.7, null)));
        when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.of(
                run(CANDIDATE_ID, "other-dataset", 15, 0.9, null)));
        assertThatThrownBy(() -> exportService.export(BASELINE_ID, CANDIDATE_ID))
                .isInstanceOf(IncompatibleEvaluationRunsException.class)
                .hasMessage("evaluation runs must use the same dataset");
    }

    @Test
    void rejectsDifferentAgentsAndDifferentScenarioCoverage() {
        AgentEvaluationRunRepository repository = mock(AgentEvaluationRunRepository.class);
        when(repository.findById(BASELINE_ID)).thenReturn(Optional.of(
                run(BASELINE_ID, "catalog-response-v1", 10, 0.7, null)));
        when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.of(
                run(CANDIDATE_ID, "catalog-response-v1", 15, "support-router", List.of(
                        result("scenario-1", 0.9, List.of(), null)))));

        assertThatThrownBy(() -> new AgentEvaluationComparisonApplicationService(repository)
                .compare(BASELINE_ID, CANDIDATE_ID))
                .isInstanceOf(IncompatibleEvaluationRunsException.class)
                .extracting(exception -> ((IncompatibleEvaluationRunsException) exception).reason())
                .isEqualTo(IncompatibleEvaluationRunsException.Reason.AGENT);

        when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.of(
                run(CANDIDATE_ID, "catalog-response-v1", 15, "catalog-specialist", List.of(
                        result("different-scenario", 0.9, List.of(), null)))));
        assertThatThrownBy(() -> new AgentEvaluationComparisonApplicationService(repository)
                .compare(BASELINE_ID, CANDIDATE_ID))
                .isInstanceOf(IncompatibleEvaluationRunsException.class)
                .extracting(exception -> ((IncompatibleEvaluationRunsException) exception).reason())
                .isEqualTo(IncompatibleEvaluationRunsException.Reason.SCENARIO_COVERAGE);

        when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.of(run(
                CANDIDATE_ID, "catalog-response-v1", 15, "catalog-specialist", List.of(
                        result("scenario-1", 0.9, List.of(), null),
                        result("scenario-1", 0.9, List.of(), null)))));
        assertThatThrownBy(() -> new AgentEvaluationComparisonApplicationService(repository)
                .compare(BASELINE_ID, CANDIDATE_ID))
                .isInstanceOf(IncompatibleEvaluationRunsException.class)
                .extracting(exception -> ((IncompatibleEvaluationRunsException) exception).reason())
                .isEqualTo(IncompatibleEvaluationRunsException.Reason.SCENARIO_COVERAGE);
    }

    @Test
    void leavesSpecializedQualityDeltaUnavailableWhenItsScenarioCoverageDiffers() {
        AgentEvaluationRunRepository repository = mock(AgentEvaluationRunRepository.class);
        AgentEvaluationExecutionMetadata routed = metadataWithIntent("CATALOG_SEARCH");
        when(repository.findById(BASELINE_ID)).thenReturn(Optional.of(run(
                BASELINE_ID, "catalog-response-v1", 10, "catalog-specialist", List.of(
                        result("scenario-1", 0.8, List.of("intent_accuracy"), routed),
                        result("scenario-2", 0.8, List.of(), null)))));
        when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.of(run(
                CANDIDATE_ID, "catalog-response-v1", 15, "catalog-specialist", List.of(
                        result("scenario-1", 0.8, List.of(), null),
                        result("scenario-2", 0.8, List.of("intent_accuracy"), routed)))));

        var comparison = new AgentEvaluationComparisonApplicationService(repository)
                .compare(BASELINE_ID, CANDIDATE_ID)
                .orElseThrow();

        assertThat(comparison.qualityDelta().intentAccuracyRateDelta()).isNull();
        assertThat(comparison.assessment().unavailableDimensions()).contains("intent_accuracy");
        assertThat(comparison.assessment().outcome())
                .isEqualTo(AgentEvaluationComparisonAssessment.Outcome.NO_QUALITY_CHANGE);
    }

    @Test
    void comparesSpecializedQualityDimensionWhenScenarioCoverageMatches() {
        AgentEvaluationRunRepository repository = mock(AgentEvaluationRunRepository.class);
        AgentEvaluationExecutionMetadata routed = metadataWithIntent("CATALOG_SEARCH");
        when(repository.findById(BASELINE_ID)).thenReturn(Optional.of(run(
                BASELINE_ID, "catalog-response-v1", 10, "catalog-specialist", List.of(
                        result("scenario-1", 0.8, List.of("intent_accuracy"), routed),
                        result("scenario-2", 0.8, List.of(), null)))));
        when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.of(run(
                CANDIDATE_ID, "catalog-response-v1", 15, "catalog-specialist", List.of(
                        new AgentEvaluationResult("scenario-1", "catalog-response-v1", false, 0.2,
                                List.of("INTENT_MISMATCH"), routed, List.of("intent_accuracy")),
                        result("scenario-2", 0.8, List.of(), null)))));

        var comparison = new AgentEvaluationComparisonApplicationService(repository)
                .compare(BASELINE_ID, CANDIDATE_ID)
                .orElseThrow();

        assertThat(comparison.qualityDelta().intentAccuracyRateDelta()).isEqualTo(-1.0);
        assertThat(comparison.assessment().regressedDimensions()).contains("intent_accuracy");
        assertThat(comparison.assessment().outcome())
                .isEqualTo(AgentEvaluationComparisonAssessment.Outcome.QUALITY_REGRESSION);
    }

    @Test
    void routerComparisonLeavesResponseOnlyMetricsUnavailable() {
        AgentEvaluationRunRepository repository = mock(AgentEvaluationRunRepository.class);
        when(repository.findById(BASELINE_ID)).thenReturn(Optional.of(run(
                BASELINE_ID, "conversation-routing-v1", 10, "conversation-router",
                List.of(routerResult("scenario-1", true)))));
        when(repository.findById(CANDIDATE_ID)).thenReturn(Optional.of(run(
                CANDIDATE_ID, "conversation-routing-v1", 15, "conversation-router",
                List.of(routerResult("scenario-1", false)))));

        var comparison = new AgentEvaluationComparisonApplicationService(repository)
                .compare(BASELINE_ID, CANDIDATE_ID)
                .orElseThrow();

        assertThat(comparison.qualityDelta().responseValidityRateDelta()).isNull();
        assertThat(comparison.qualityDelta().responseGroundingRateDelta()).isNull();
        assertThat(comparison.qualityDelta().safetyRateDelta()).isNull();
        assertThat(comparison.qualityDelta().utilityRateDelta()).isNull();
        assertThat(comparison.qualityDelta().intentAccuracyRateDelta()).isEqualTo(-1.0);
        assertThat(comparison.qualityDelta().actionAccuracyRateDelta()).isEqualTo(-1.0);
        assertThat(comparison.qualityDelta().entityExtractionRateDelta()).isEqualTo(-1.0);
        assertThat(comparison.assessment().unavailableDimensions()).contains(
                "response_validity", "response_grounding", "safety", "utility");
        assertThat(comparison.assessment().regressedDimensions()).contains(
                "intent_accuracy", "action_accuracy", "entity_extraction")
                .doesNotContain("utility", "safety", "response_validity", "response_grounding");
    }

    @Test
    void rejectsAnEvidenceExportWithTooManyScenarios() {
        var scenarios = java.util.stream.IntStream.range(0, AgentEvaluationEvidenceExport.MAX_SCENARIOS + 1)
                .mapToObj(index -> new AgentEvaluationScenarioComparison(
                        "scenario-" + index, true, true, 1.0, 1.0, 0.0))
                .toList();
        var comparison = new AgentEvaluationComparison(
                BASELINE_ID,
                CANDIDATE_ID,
                "catalog-response-v1",
                summary(BASELINE_ID),
                summary(CANDIDATE_ID),
                new AgentEvaluationMetricDelta(
                        0, 0, 0, 0, 0, OptionalLong.empty(), OptionalLong.empty(), Optional.empty()),
                scenarios,
                new AgentEvaluationQualityMetricDelta(0, 0, 0, 0, 0, null, null, null, null),
                new AgentEvaluationComparisonAssessment(
                        AgentEvaluationComparisonAssessment.Outcome.NO_QUALITY_CHANGE,
                        scenarios.size(), 0, 0, scenarios.size(), List.of(), List.of(), List.of(),
                        AgentEvaluationComparisonAssessment.EvidenceLevel.DESCRIPTIVE_NOT_STATISTICALLY_SIGNIFICANT));

        assertThatThrownBy(() -> AgentEvaluationEvidenceExport.from(comparison))
                .isInstanceOf(EvaluationEvidenceExportLimitException.class)
                .hasMessage("evaluation evidence export exceeds the maximum scenario limit");
    }

    private static AgentEvaluationRunSummary summary(UUID runId) {
        return new AgentEvaluationRunSummary(
                runId,
                "catalog-response-v1",
                "catalog-specialist",
                "v1",
                "mock",
                "deterministic-v1",
                Instant.parse("2026-09-08T00:00:00Z"),
                Instant.parse("2026-09-08T00:00:01Z"),
                1,
                1,
                0,
                1,
                1.0,
                1.0,
                Map.of());
    }

    private static AgentEvaluationRun run(
            UUID runId,
            String datasetVersion,
            long durationMs,
            double score,
            AgentEvaluationExecutionMetadata metadata) {
        return run(runId, datasetVersion, durationMs, "catalog-specialist", List.of(
                result("scenario-1", score, List.of(), metadata)));
    }

    private static AgentEvaluationRun run(
            UUID runId,
            String datasetVersion,
            long durationMs,
            String agentId,
            List<AgentEvaluationResult> scenarios) {
        List<AgentEvaluationResult> normalizedScenarios = scenarios.stream()
                .map(scenario -> new AgentEvaluationResult(
                        scenario.scenarioId(),
                        datasetVersion,
                        scenario.passed(),
                        scenario.score(),
                        scenario.reasons(),
                        scenario.executionMetadata(),
                        scenario.evaluatedDimensions()))
                .toList();
        int passed = (int) normalizedScenarios.stream().filter(AgentEvaluationResult::passed).count();
        double averageScore = normalizedScenarios.stream().mapToDouble(AgentEvaluationResult::score).average().orElseThrow();
        return new AgentEvaluationRun(
                runId,
                datasetVersion,
                agentId,
                "v1",
                "mock",
                "deterministic-v1",
                Instant.parse("2026-09-08T00:00:00Z"),
                Instant.parse("2026-09-08T00:00:01Z"),
                durationMs,
                new AgentEvaluationSuiteResult(
                        datasetVersion,
                        normalizedScenarios,
                        normalizedScenarios.size(),
                        passed,
                        normalizedScenarios.size() - passed,
                        (double) passed / normalizedScenarios.size(),
                        averageScore,
                        Map.of()));
    }

    private static AgentEvaluationResult result(
            String scenarioId,
            double score,
            List<String> evaluatedDimensions,
            AgentEvaluationExecutionMetadata metadata) {
        return new AgentEvaluationResult(
                scenarioId, "catalog-response-v1", true, score, List.of(), metadata, evaluatedDimensions);
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

    private static AgentEvaluationExecutionMetadata metadataWithIntent(String routedIntent) {
        return new AgentEvaluationExecutionMetadata(
                "catalog-specialist", "v1", "mock", "deterministic-v1", 1, 1L,
                1, 1, 2, BigDecimal.ZERO, "test-pricing-v1", routedIntent,
                null, null, null, null);
    }

    private static AgentEvaluationResult routerResult(String scenarioId, boolean matches) {
        String intent = matches ? "CATALOG_SEARCH" : "GENERAL_SUPPORT";
        String action = matches ? "CATALOG_SEARCH" : "GENERAL_SUPPORT";
        List<String> entities = matches ? List.of("productType=remera") : List.of();
        List<String> reasons = matches ? List.of() : List.of(
                "INTENT_MISMATCH", "ACTION_MISMATCH", "ENTITY_EXTRACTION_MISMATCH");
        AgentEvaluationExecutionMetadata metadata = new AgentEvaluationExecutionMetadata(
                "conversation-router", "1", "bedrock", "model-v1", 10, 8L,
                10, 5, 15, new BigDecimal("0.0001"), "pricing-v1",
                intent, action, entities, null, null, null);
        return new AgentEvaluationResult(
                scenarioId, "conversation-routing-v1", matches, matches ? 1.0 : 0.0,
                reasons, metadata, List.of("intent_accuracy", "action_accuracy", "entity_extraction"));
    }
}
