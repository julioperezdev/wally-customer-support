package com.wally.customersupport.agent.infrastructure.http;

import java.util.Objects;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRunRequest;

/** HTTP input for a controlled evaluation run; it contains no executable data. */
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
        provider = required(provider, "provider");
        modelId = required(modelId, "modelId");
    }

    AgentEvaluationRunRequest toApplicationRequest() {
        return new AgentEvaluationRunRequest(
                datasetVersion,
                agentId,
                agentVersion,
                provider,
                modelId);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
