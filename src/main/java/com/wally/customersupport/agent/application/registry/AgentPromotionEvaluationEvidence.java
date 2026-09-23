package com.wally.customersupport.agent.application.registry;

import java.util.Objects;
import java.util.UUID;

/** Sanitized link to the comparable evaluation used during human promotion review. */
public record AgentPromotionEvaluationEvidence(
        UUID baselineRunId,
        UUID candidateRunId,
        String datasetVersion,
        String assessmentOutcome) {

    public AgentPromotionEvaluationEvidence {
        baselineRunId = Objects.requireNonNull(baselineRunId, "baselineRunId");
        candidateRunId = Objects.requireNonNull(candidateRunId, "candidateRunId");
        if (baselineRunId.equals(candidateRunId)) {
            throw new IllegalArgumentException("evaluation run IDs must differ");
        }
        datasetVersion = required(datasetVersion, "datasetVersion");
        assessmentOutcome = required(assessmentOutcome, "assessmentOutcome");
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
