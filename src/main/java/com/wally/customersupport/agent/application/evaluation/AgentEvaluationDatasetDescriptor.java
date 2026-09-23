package com.wally.customersupport.agent.application.evaluation;

import java.util.Objects;

/** Content-free list item for available immutable evaluation suites. */
public record AgentEvaluationDatasetDescriptor(String datasetVersion, String agentId, int scenarioCount) {

    public AgentEvaluationDatasetDescriptor {
        datasetVersion = required(datasetVersion, "datasetVersion");
        agentId = required(agentId, "agentId");
        if (scenarioCount < 1) {
            throw new IllegalArgumentException("scenarioCount must be positive");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
