package com.wally.customersupport.agent.application.evaluation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Sanitized comparison of two runs from the same evaluation dataset. */
public record AgentEvaluationComparison(
        UUID baselineRunId,
        UUID candidateRunId,
        String datasetVersion,
        AgentEvaluationRunSummary baseline,
        AgentEvaluationRunSummary candidate,
        AgentEvaluationMetricDelta metricDelta,
        List<AgentEvaluationScenarioComparison> scenarios) {

    public AgentEvaluationComparison {
        baselineRunId = Objects.requireNonNull(baselineRunId, "baselineRunId");
        candidateRunId = Objects.requireNonNull(candidateRunId, "candidateRunId");
        if (baselineRunId.equals(candidateRunId)) {
            throw new IllegalArgumentException("baselineRunId and candidateRunId must differ");
        }
        datasetVersion = required(datasetVersion);
        baseline = Objects.requireNonNull(baseline, "baseline");
        candidate = Objects.requireNonNull(candidate, "candidate");
        metricDelta = Objects.requireNonNull(metricDelta, "metricDelta");
        scenarios = scenarios == null ? List.of() : scenarios.stream()
                .map(scenario -> Objects.requireNonNull(scenario, "scenarios must not contain null"))
                .toList();
    }

    private static String required(String value) {
        String normalized = Objects.requireNonNull(value, "datasetVersion").strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("datasetVersion must not be blank");
        }
        return normalized;
    }
}
