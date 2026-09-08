package com.wally.customersupport.agent.application.evaluation;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Content-free summary used to list historical evaluation runs. */
public record AgentEvaluationRunSummary(
        UUID runId,
        String datasetVersion,
        String agentId,
        String agentVersion,
        String provider,
        String modelId,
        Instant startedAt,
        Instant completedAt,
        long durationMs,
        int totalScenarios,
        int passedScenarios,
        int failedScenarios,
        double passRate,
        double averageScore,
        Map<String, Integer> failureReasons) {

    public AgentEvaluationRunSummary {
        runId = Objects.requireNonNull(runId, "runId");
        datasetVersion = required(datasetVersion, "datasetVersion");
        agentId = required(agentId, "agentId");
        agentVersion = required(agentVersion, "agentVersion");
        provider = required(provider, "provider");
        modelId = required(modelId, "modelId");
        startedAt = Objects.requireNonNull(startedAt, "startedAt");
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        if (totalScenarios < 1 || passedScenarios < 0 || failedScenarios < 0
                || passedScenarios + failedScenarios != totalScenarios) {
            throw new IllegalArgumentException("scenario counts are inconsistent");
        }
        if (Double.isNaN(passRate) || passRate < 0 || passRate > 1
                || Double.isNaN(averageScore) || averageScore < 0 || averageScore > 1) {
            throw new IllegalArgumentException("metrics must be between 0 and 1");
        }
        failureReasons = failureReasons == null ? Map.of() : Map.copyOf(failureReasons);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
