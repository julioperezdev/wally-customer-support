package com.wally.customersupport.agent.application.evaluation;

import java.util.Objects;
import com.wally.customersupport.agent.domain.model.AgentVersion;

/** Stable input for starting an evaluation run without accepting prompt content. */
public record AgentEvaluationRunRequest(
        String datasetVersion,
        String agentId,
        String agentVersion,
        String provider,
        String modelId,
        AgentVersion versionDefinition) {

    /** Compatibility constructor for deterministic callers and existing API clients. */
    public AgentEvaluationRunRequest(
            String datasetVersion,
            String agentId,
            String agentVersion,
            String provider,
            String modelId) {
        this(datasetVersion, agentId, agentVersion, provider, modelId, null);
    }

    /** Provider and model are resolved from SQL, never supplied as execution authority. */
    public AgentEvaluationRunRequest(String datasetVersion, String agentId, String agentVersion) {
        this(datasetVersion, agentId, agentVersion, null, null, null);
    }

    public AgentEvaluationRunRequest {
        datasetVersion = required(datasetVersion, "datasetVersion");
        agentId = required(agentId, "agentId");
        agentVersion = required(agentVersion, "agentVersion");
        provider = optional(provider);
        modelId = optional(modelId);
        if (versionDefinition != null
                && (!agentId.equals(versionDefinition.agentId())
                        || !Integer.toString(versionDefinition.version()).equals(agentVersion)
                        || !versionDefinition.modelProvider().equals(provider)
                        || !versionDefinition.modelId().equals(modelId))) {
            throw new IllegalArgumentException("resolved evaluation identity does not match the selected version");
        }
    }

    public AgentEvaluationRunRequest withVersionDefinition(AgentVersion definition) {
        AgentVersion resolved = Objects.requireNonNull(definition, "definition");
        return new AgentEvaluationRunRequest(
                datasetVersion,
                resolved.agentId(),
                Integer.toString(resolved.version()),
                resolved.modelProvider(),
                resolved.modelId(),
                resolved);
    }

    @Override
    public String toString() {
        return "AgentEvaluationRunRequest[datasetVersion=" + datasetVersion + ", agentId=" + agentId
                + ", agentVersion=" + agentVersion + ", provider=" + provider + ", modelId=" + modelId
                + ", versionDefinition=<not-rendered>]";
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
