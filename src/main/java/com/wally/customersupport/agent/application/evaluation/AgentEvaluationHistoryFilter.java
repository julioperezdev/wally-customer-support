package com.wally.customersupport.agent.application.evaluation;

import java.time.Instant;
import java.util.Objects;

/** Optional, normalized filters for read-only evaluation history queries. */
public record AgentEvaluationHistoryFilter(
        String datasetVersion,
        String agentId,
        String agentVersion,
        String provider,
        String modelId,
        Instant completedFrom,
        Instant completedTo) {

    public AgentEvaluationHistoryFilter {
        datasetVersion = optionalText(datasetVersion, "datasetVersion");
        agentId = optionalText(agentId, "agentId");
        agentVersion = optionalText(agentVersion, "agentVersion");
        provider = optionalText(provider, "provider");
        modelId = optionalText(modelId, "modelId");
        if (completedFrom != null && completedTo != null && completedFrom.isAfter(completedTo)) {
            throw new IllegalArgumentException("completedFrom must not be after completedTo");
        }
    }

    public static AgentEvaluationHistoryFilter all() {
        return new AgentEvaluationHistoryFilter(null, null, null, null, null, null, null);
    }

    private static String optionalText(String value, String field) {
        if (value == null) {
            return null;
        }
        String normalized = Objects.requireNonNull(value, field).strip();
        return normalized.isBlank() ? null : normalized;
    }
}
