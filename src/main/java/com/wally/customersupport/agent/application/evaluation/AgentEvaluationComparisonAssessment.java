package com.wally.customersupport.agent.application.evaluation;

import java.util.List;
import java.util.Objects;

/** Descriptive comparison outcome, never a publication or activation decision. */
public record AgentEvaluationComparisonAssessment(
        Outcome outcome,
        int scenarioCount,
        int improvedScenarioCount,
        int regressedScenarioCount,
        int unchangedScenarioCount,
        List<String> improvedDimensions,
        List<String> regressedDimensions,
        List<String> unavailableDimensions,
        EvidenceLevel evidenceLevel) {

    public enum Outcome {
        QUALITY_IMPROVED,
        QUALITY_REGRESSION,
        MIXED,
        NO_QUALITY_CHANGE
    }

    public enum EvidenceLevel {
        DESCRIPTIVE_NOT_STATISTICALLY_SIGNIFICANT
    }

    public AgentEvaluationComparisonAssessment {
        outcome = Objects.requireNonNull(outcome, "outcome");
        if (scenarioCount < 1 || improvedScenarioCount < 0 || regressedScenarioCount < 0
                || unchangedScenarioCount < 0
                || improvedScenarioCount + regressedScenarioCount + unchangedScenarioCount != scenarioCount) {
            throw new IllegalArgumentException("assessment scenario counts are inconsistent");
        }
        improvedDimensions = normalize(improvedDimensions);
        regressedDimensions = normalize(regressedDimensions);
        unavailableDimensions = normalize(unavailableDimensions);
        evidenceLevel = Objects.requireNonNull(evidenceLevel, "evidenceLevel");
    }

    private static List<String> normalize(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }
}
