package com.wally.customersupport.agent.application.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationComparison;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationComparisonAssessment;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationMetricDelta;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationOperationalMetrics;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationQualityMetricDelta;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRun;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRunSummary;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationScenarioComparison;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationRunRepository;
import com.wally.customersupport.agent.domain.model.AgentEvaluationExecutionMetadata;
import com.wally.customersupport.agent.domain.model.AgentEvaluationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Compares sanitized evaluation runs without selecting or promoting a winner. */
@Service
@RequiredArgsConstructor
public class AgentEvaluationComparisonApplicationService {

    private static final double QUALITY_DELTA_TOLERANCE = 0.000001;

    private final AgentEvaluationRunRepository repository;

    public Optional<AgentEvaluationComparison> compare(UUID baselineRunId, UUID candidateRunId) {
        Objects.requireNonNull(baselineRunId, "baselineRunId");
        Objects.requireNonNull(candidateRunId, "candidateRunId");
        if (baselineRunId.equals(candidateRunId)) {
            throw new IllegalArgumentException("baselineRunId and candidateRunId must differ");
        }

        Optional<AgentEvaluationRun> baseline = repository.findById(baselineRunId);
        Optional<AgentEvaluationRun> candidate = repository.findById(candidateRunId);
        if (baseline.isEmpty() || candidate.isEmpty()) {
            return Optional.empty();
        }
        validateComparableRuns(baseline.get(), candidate.get());
        return Optional.of(toComparison(baseline.get(), candidate.get()));
    }

    private static AgentEvaluationComparison toComparison(
            AgentEvaluationRun baseline,
            AgentEvaluationRun candidate) {
        AgentEvaluationQualityMetricDelta qualityDelta = qualityDelta(baseline, candidate);
        return new AgentEvaluationComparison(
                baseline.runId(),
                candidate.runId(),
                baseline.datasetVersion(),
                summary(baseline),
                summary(candidate),
                metricDelta(baseline, candidate),
                scenarioComparisons(baseline, candidate),
                qualityDelta,
                assessment(baseline, candidate, qualityDelta));
    }

    private static void validateComparableRuns(AgentEvaluationRun baseline, AgentEvaluationRun candidate) {
        if (!baseline.datasetVersion().equals(candidate.datasetVersion())) {
            throw new IncompatibleEvaluationRunsException(IncompatibleEvaluationRunsException.Reason.DATASET);
        }
        if (!baseline.agentId().equals(candidate.agentId())) {
            throw new IncompatibleEvaluationRunsException(IncompatibleEvaluationRunsException.Reason.AGENT);
        }
        if (!scenarioInventory(baseline).equals(scenarioInventory(candidate))) {
            throw new IncompatibleEvaluationRunsException(IncompatibleEvaluationRunsException.Reason.SCENARIO_COVERAGE);
        }
    }

    private static Set<String> scenarioInventory(AgentEvaluationRun run) {
        List<AgentEvaluationResult> results = run.suiteResult().scenarioResults();
        Set<String> scenarioIds = results.stream()
                .map(AgentEvaluationResult::scenarioId)
                .collect(Collectors.toCollection(HashSet::new));
        if (scenarioIds.size() != results.size()) {
            throw new IncompatibleEvaluationRunsException(IncompatibleEvaluationRunsException.Reason.SCENARIO_COVERAGE);
        }
        return scenarioIds;
    }

    private static AgentEvaluationRunSummary summary(AgentEvaluationRun run) {
        var result = run.suiteResult();
        var operationalMetrics = AgentEvaluationOperationalMetrics.from(result.scenarioResults());
        return new AgentEvaluationRunSummary(
                run.runId(),
                run.datasetVersion(),
                run.agentId(),
                run.agentVersion(),
                run.provider(),
                run.modelId(),
                run.startedAt(),
                run.completedAt(),
                run.durationMs(),
                result.totalScenarios(),
                result.passedScenarios(),
                result.failedScenarios(),
                result.passRate(),
                result.averageScore(),
                result.failureReasons(),
                operationalMetrics.totalTokens(),
                operationalMetrics.providerLatencyMs(),
                operationalMetrics.estimatedCostUsd());
    }

    private static AgentEvaluationMetricDelta metricDelta(
            AgentEvaluationRun baseline,
            AgentEvaluationRun candidate) {
        var baselineResult = baseline.suiteResult();
        var candidateResult = candidate.suiteResult();
        return new AgentEvaluationMetricDelta(
                candidateResult.passedScenarios() - baselineResult.passedScenarios(),
                candidateResult.failedScenarios() - baselineResult.failedScenarios(),
                candidateResult.passRate() - baselineResult.passRate(),
                candidateResult.averageScore() - baselineResult.averageScore(),
                candidate.durationMs() - baseline.durationMs(),
                subtractOptional(sumMetadata(baseline, AgentEvaluationExecutionMetadata::totalTokens),
                        sumMetadata(candidate, AgentEvaluationExecutionMetadata::totalTokens)),
                subtractOptional(sumMetadata(baseline, AgentEvaluationExecutionMetadata::providerLatencyMs),
                        sumMetadata(candidate, AgentEvaluationExecutionMetadata::providerLatencyMs)),
                subtractOptional(sumCost(baseline), sumCost(candidate)));
    }

    private static AgentEvaluationQualityMetricDelta qualityDelta(
            AgentEvaluationRun baseline,
            AgentEvaluationRun candidate) {
        var before = baseline.suiteResult().qualityScorecard();
        var after = candidate.suiteResult().qualityScorecard();
        return new AgentEvaluationQualityMetricDelta(
                candidate.suiteResult().passRate() - baseline.suiteResult().passRate(),
                subtractOptional(before.responseValidityRate(), after.responseValidityRate()),
                subtractOptional(before.responseGroundingRate(), after.responseGroundingRate()),
                subtractOptional(before.safetyRate(), after.safetyRate()),
                subtractOptional(before.utilityRate(), after.utilityRate()),
                comparableDimensionDelta(baseline, candidate, "intent_accuracy",
                        before.intentAccuracyRate(), after.intentAccuracyRate()),
                comparableDimensionDelta(baseline, candidate, "entity_extraction",
                        before.entityExtractionRate(), after.entityExtractionRate()),
                comparableDimensionDelta(baseline, candidate, "action_accuracy",
                        before.actionAccuracyRate(), after.actionAccuracyRate()),
                comparableDimensionDelta(baseline, candidate, "quantity_extraction",
                        before.quantityExtractionRate(), after.quantityExtractionRate()),
                comparableDimensionDelta(baseline, candidate, "tool_success",
                        before.toolSuccessRate(), after.toolSuccessRate()),
                comparableDimensionDelta(baseline, candidate, "rag_grounding",
                        before.ragGroundingRate(), after.ragGroundingRate()));
    }

    private static Double comparableDimensionDelta(
            AgentEvaluationRun baseline,
            AgentEvaluationRun candidate,
            String dimension,
            Double baselineRate,
            Double candidateRate) {
        if (baselineRate == null || candidateRate == null
                || !dimensionCoverage(baseline, dimension).equals(dimensionCoverage(candidate, dimension))) {
            return null;
        }
        return candidateRate - baselineRate;
    }

    private static Set<String> dimensionCoverage(AgentEvaluationRun run, String dimension) {
        return run.suiteResult().scenarioResults().stream()
                .filter(result -> result.evaluatedDimensions().contains(dimension))
                .map(AgentEvaluationResult::scenarioId)
                .collect(Collectors.toSet());
    }

    private static AgentEvaluationComparisonAssessment assessment(
            AgentEvaluationRun baseline,
            AgentEvaluationRun candidate,
            AgentEvaluationQualityMetricDelta deltas) {
        List<String> improvements = new ArrayList<>();
        List<String> regressions = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        collectDimension("pass_rate", deltas.passRateDelta(), improvements, regressions);
        collectOptionalDimension("response_validity", deltas.responseValidityRateDelta(), improvements, regressions, unavailable);
        collectOptionalDimension("response_grounding", deltas.responseGroundingRateDelta(), improvements, regressions, unavailable);
        collectOptionalDimension("safety", deltas.safetyRateDelta(), improvements, regressions, unavailable);
        collectOptionalDimension("utility", deltas.utilityRateDelta(), improvements, regressions, unavailable);
        collectOptionalDimension("intent_accuracy", deltas.intentAccuracyRateDelta(), improvements, regressions, unavailable);
        collectOptionalDimension("entity_extraction", deltas.entityExtractionRateDelta(), improvements, regressions, unavailable);
        collectOptionalDimension("action_accuracy", deltas.actionAccuracyRateDelta(), improvements, regressions, unavailable);
        collectOptionalDimension("tool_success", deltas.toolSuccessRateDelta(), improvements, regressions, unavailable);
        collectOptionalDimension("rag_grounding", deltas.ragGroundingRateDelta(), improvements, regressions, unavailable);

        int improvedScenarios = 0;
        int regressedScenarios = 0;
        int unchangedScenarios = 0;
        Map<String, AgentEvaluationResult> before = byScenario(baseline);
        Map<String, AgentEvaluationResult> after = byScenario(candidate);
        for (String scenarioId : before.keySet()) {
            AgentEvaluationResult baselineResult = before.get(scenarioId);
            AgentEvaluationResult candidateResult = after.get(scenarioId);
            double scoreDelta = candidateResult.score() - baselineResult.score();
            if ((baselineResult.passed() && !candidateResult.passed())
                    || scoreDelta < -QUALITY_DELTA_TOLERANCE) {
                regressedScenarios++;
            } else if ((!baselineResult.passed() && candidateResult.passed())
                    || scoreDelta > QUALITY_DELTA_TOLERANCE) {
                improvedScenarios++;
            } else {
                unchangedScenarios++;
            }
        }

        boolean improved = !improvements.isEmpty() || improvedScenarios > 0;
        boolean regressed = !regressions.isEmpty() || regressedScenarios > 0;
        AgentEvaluationComparisonAssessment.Outcome outcome = improved && regressed
                ? AgentEvaluationComparisonAssessment.Outcome.MIXED
                : regressed
                        ? AgentEvaluationComparisonAssessment.Outcome.QUALITY_REGRESSION
                        : improved
                                ? AgentEvaluationComparisonAssessment.Outcome.QUALITY_IMPROVED
                                : AgentEvaluationComparisonAssessment.Outcome.NO_QUALITY_CHANGE;
        return new AgentEvaluationComparisonAssessment(
                outcome,
                before.size(),
                improvedScenarios,
                regressedScenarios,
                unchangedScenarios,
                improvements.stream().sorted(Comparator.naturalOrder()).toList(),
                regressions.stream().sorted(Comparator.naturalOrder()).toList(),
                unavailable.stream().sorted(Comparator.naturalOrder()).toList(),
                AgentEvaluationComparisonAssessment.EvidenceLevel.DESCRIPTIVE_NOT_STATISTICALLY_SIGNIFICANT);
    }

    private static void collectOptionalDimension(
            String name,
            Double delta,
            List<String> improvements,
            List<String> regressions,
            List<String> unavailable) {
        if (delta == null) {
            unavailable.add(name);
            return;
        }
        collectDimension(name, delta, improvements, regressions);
    }

    private static void collectDimension(
            String name,
            double delta,
            List<String> improvements,
            List<String> regressions) {
        if (delta > QUALITY_DELTA_TOLERANCE) {
            improvements.add(name);
        } else if (delta < -QUALITY_DELTA_TOLERANCE) {
            regressions.add(name);
        }
    }

    private static List<AgentEvaluationScenarioComparison> scenarioComparisons(
            AgentEvaluationRun baseline,
            AgentEvaluationRun candidate) {
        Map<String, AgentEvaluationResult> baselineResults = byScenario(baseline);
        Map<String, AgentEvaluationResult> candidateResults = byScenario(candidate);
        return java.util.stream.Stream.concat(baselineResults.keySet().stream(), candidateResults.keySet().stream())
                .distinct()
                .sorted()
                .map(scenarioId -> scenarioComparison(
                        scenarioId, baselineResults.get(scenarioId), candidateResults.get(scenarioId)))
                .toList();
    }

    private static Map<String, AgentEvaluationResult> byScenario(AgentEvaluationRun run) {
        return run.suiteResult().scenarioResults().stream()
                .collect(Collectors.toMap(
                        AgentEvaluationResult::scenarioId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private static AgentEvaluationScenarioComparison scenarioComparison(
            String scenarioId,
            AgentEvaluationResult baseline,
            AgentEvaluationResult candidate) {
        Double baselineScore = baseline == null ? null : baseline.score();
        Double candidateScore = candidate == null ? null : candidate.score();
        return new AgentEvaluationScenarioComparison(
                scenarioId,
                baseline == null ? null : baseline.passed(),
                candidate == null ? null : candidate.passed(),
                baselineScore,
                candidateScore,
                baselineScore == null || candidateScore == null ? null : candidateScore - baselineScore);
    }

    private static OptionalLong sumMetadata(
            AgentEvaluationRun run,
            Function<AgentEvaluationExecutionMetadata, ? extends Number> metric) {
        List<AgentEvaluationExecutionMetadata> metadata = executionMetadata(run);
        if (metadata.stream().anyMatch(value -> metric.apply(value) == null)) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(metadata.stream().mapToLong(value -> metric.apply(value).longValue()).sum());
    }

    private static Optional<BigDecimal> sumCost(AgentEvaluationRun run) {
        List<AgentEvaluationExecutionMetadata> metadata = executionMetadata(run);
        if (metadata.stream().anyMatch(value -> value.estimatedCostUsd() == null)) {
            return Optional.empty();
        }
        return Optional.of(metadata.stream()
                .map(AgentEvaluationExecutionMetadata::estimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static List<AgentEvaluationExecutionMetadata> executionMetadata(AgentEvaluationRun run) {
        List<AgentEvaluationExecutionMetadata> metadata = new ArrayList<>();
        for (AgentEvaluationResult result : run.suiteResult().scenarioResults()) {
            if (result.executionMetadata() == null) {
                return List.of(new AgentEvaluationExecutionMetadata(
                        null, null, null, null, 0, null, null, null, null, null, null));
            }
            metadata.add(result.executionMetadata());
        }
        return metadata;
    }

    private static OptionalLong subtractOptional(OptionalLong baseline, OptionalLong candidate) {
        if (baseline.isEmpty() || candidate.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(candidate.getAsLong() - baseline.getAsLong());
    }

    private static Optional<BigDecimal> subtractOptional(
            Optional<BigDecimal> baseline,
            Optional<BigDecimal> candidate) {
        if (baseline.isEmpty() || candidate.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidate.get().subtract(baseline.get()));
    }

    private static Double subtractOptional(Double baseline, Double candidate) {
        if (baseline == null || candidate == null) {
            return null;
        }
        return candidate - baseline;
    }
}
