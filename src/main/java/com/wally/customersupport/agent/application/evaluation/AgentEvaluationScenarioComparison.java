package com.wally.customersupport.agent.application.evaluation;

import java.util.Objects;

/** Sanitized comparison of one scenario identified only by its stable ID. */
public record AgentEvaluationScenarioComparison(
        String scenarioId,
        Boolean baselinePassed,
        Boolean candidatePassed,
        Double baselineScore,
        Double candidateScore,
        Double scoreDelta) {

    public AgentEvaluationScenarioComparison {
        scenarioId = required(scenarioId);
        if (baselineScore != null && (Double.isNaN(baselineScore) || baselineScore < 0 || baselineScore > 1)) {
            throw new IllegalArgumentException("baselineScore must be between 0 and 1");
        }
        if (candidateScore != null && (Double.isNaN(candidateScore) || candidateScore < 0 || candidateScore > 1)) {
            throw new IllegalArgumentException("candidateScore must be between 0 and 1");
        }
        if (scoreDelta != null && Double.isNaN(scoreDelta)) {
            throw new IllegalArgumentException("scoreDelta must be numeric");
        }
        if (baselinePassed == null && baselineScore != null) {
            throw new IllegalArgumentException("baseline score requires a baseline scenario");
        }
        if (candidatePassed == null && candidateScore != null) {
            throw new IllegalArgumentException("candidate score requires a candidate scenario");
        }
    }

    public boolean changed() {
        return !Objects.equals(baselinePassed, candidatePassed)
                || !Objects.equals(baselineScore, candidateScore);
    }

    private static String required(String value) {
        String normalized = Objects.requireNonNull(value, "scenarioId").strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("scenarioId must not be blank");
        }
        return normalized;
    }
}
