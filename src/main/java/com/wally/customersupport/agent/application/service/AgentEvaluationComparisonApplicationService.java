package com.wally.customersupport.agent.application.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationComparison;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationMetricDelta;
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
        if (!baseline.get().datasetVersion().equals(candidate.get().datasetVersion())) {
            throw new IncompatibleEvaluationDatasetException();
        }
        return Optional.of(toComparison(baseline.get(), candidate.get()));
    }

    private static AgentEvaluationComparison toComparison(
            AgentEvaluationRun baseline,
            AgentEvaluationRun candidate) {
        return new AgentEvaluationComparison(
                baseline.runId(),
                candidate.runId(),
                baseline.datasetVersion(),
                summary(baseline),
                summary(candidate),
                metricDelta(baseline, candidate),
                scenarioComparisons(baseline, candidate));
    }

    private static AgentEvaluationRunSummary summary(AgentEvaluationRun run) {
        var result = run.suiteResult();
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
                result.failureReasons());
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
}
