package com.wally.customersupport.agent.application.evaluation;

import java.util.Objects;

/** Stable input for starting an evaluation run without accepting prompt content. */
public record AgentEvaluationRunRequest(
        String datasetVersion,
        String agentId,
        String agentVersion,
        String provider,
        String modelId) {

    public AgentEvaluationRunRequest {
        datasetVersion = required(datasetVersion, "datasetVersion");
        agentId = required(agentId, "agentId");
        agentVersion = required(agentVersion, "agentVersion");
        provider = required(provider, "provider");
        modelId = required(modelId, "modelId");
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
