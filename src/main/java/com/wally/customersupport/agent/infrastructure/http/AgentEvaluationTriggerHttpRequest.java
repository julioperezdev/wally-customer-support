package com.wally.customersupport.agent.infrastructure.http;

import java.util.Objects;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRunRequest;

/** HTTP input for a controlled evaluation run; SQL supplies provider and model configuration. */
public record AgentEvaluationTriggerHttpRequest(
        String datasetVersion,
        String agentId,
        String agentVersion,
        String provider,
        String modelId) {

    public AgentEvaluationTriggerHttpRequest {
        datasetVersion = required(datasetVersion, "datasetVersion");
        agentId = required(agentId, "agentId");
        agentVersion = required(agentVersion, "agentVersion");
        provider = optional(provider);
        modelId = optional(modelId);
    }

    AgentEvaluationRunRequest toApplicationRequest() {
        if (provider != null && modelId != null) {
            return new AgentEvaluationRunRequest(datasetVersion, agentId, agentVersion, provider, modelId);
        }
        return new AgentEvaluationRunRequest(
                datasetVersion,
                agentId,
                agentVersion);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
