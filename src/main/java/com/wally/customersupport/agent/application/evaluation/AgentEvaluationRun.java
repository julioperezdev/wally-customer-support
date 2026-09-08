package com.wally.customersupport.agent.application.evaluation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.wally.customersupport.agent.domain.model.AgentEvaluationSuiteResult;

/** Sanitized outcome of one traceable evaluation run. */
public record AgentEvaluationRun(
        UUID runId,
        String datasetVersion,
        String agentId,
        String agentVersion,
        String provider,
        String modelId,
        Instant startedAt,
        Instant completedAt,
        long durationMs,
        AgentEvaluationSuiteResult suiteResult) {

    public AgentEvaluationRun {
        runId = Objects.requireNonNull(runId, "runId");
        datasetVersion = required(datasetVersion, "datasetVersion");
        agentId = required(agentId, "agentId");
        agentVersion = required(agentVersion, "agentVersion");
        provider = required(provider, "provider");
        modelId = required(modelId, "modelId");
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt must not be before startedAt");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        suiteResult = Objects.requireNonNull(suiteResult, "suiteResult");
        if (!datasetVersion.equals(suiteResult.datasetVersion())) {
            throw new IllegalArgumentException("datasetVersion must match suiteResult");
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
