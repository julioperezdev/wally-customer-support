package com.wally.customersupport.agent.domain.model;

import java.util.List;
import java.util.Objects;

/** Sanitized result of evaluating one scenario. */
public record AgentEvaluationResult(
        String scenarioId,
        String datasetVersion,
        boolean passed,
        double score,
        List<String> reasons,
        AgentEvaluationExecutionMetadata executionMetadata) {

    public AgentEvaluationResult {
        scenarioId = required(scenarioId, "scenarioId");
        datasetVersion = required(datasetVersion, "datasetVersion");
        if (Double.isNaN(score) || score < 0 || score > 1) {
            throw new IllegalArgumentException("score must be between 0 and 1");
        }
        reasons = reasons == null ? List.of() : reasons.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (passed && !reasons.isEmpty()) {
            throw new IllegalArgumentException("passed result must not contain failure reasons");
        }
        if (!passed && reasons.isEmpty()) {
            throw new IllegalArgumentException("failed result requires a reason");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
