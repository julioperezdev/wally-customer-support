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
        List<AgentEvaluationScenarioComparison> scenarios,
        AgentEvaluationQualityMetricDelta qualityDelta,
        AgentEvaluationComparisonAssessment assessment) {

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
        qualityDelta = Objects.requireNonNull(qualityDelta, "qualityDelta");
        assessment = Objects.requireNonNull(assessment, "assessment");
        if (!datasetVersion.equals(baseline.datasetVersion())
                || !datasetVersion.equals(candidate.datasetVersion())) {
            throw new IllegalArgumentException("comparison datasetVersion must match both runs");
        }
        if (!baseline.agentId().equals(candidate.agentId())) {
            throw new IllegalArgumentException("comparison runs must use the same logical agent");
        }
        scenarios = scenarios == null ? List.of() : scenarios.stream()
                .map(scenario -> Objects.requireNonNull(scenario, "scenarios must not contain null"))
                .toList();
        if (scenarios.stream().anyMatch(scenario -> scenario.baselinePassed() == null
                || scenario.candidatePassed() == null)) {
            throw new IllegalArgumentException("comparison scenarios must exist in both runs");
        }
        if (scenarios.stream().map(AgentEvaluationScenarioComparison::scenarioId).distinct().count() != scenarios.size()) {
            throw new IllegalArgumentException("comparison scenario IDs must be unique");
        }
        if (assessment.scenarioCount() != scenarios.size()) {
            throw new IllegalArgumentException("assessment must match compared scenarios");
        }
    }

    private static String required(String value) {
        String normalized = Objects.requireNonNull(value, "datasetVersion").strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("datasetVersion must not be blank");
        }
        return normalized;
    }
}
