package com.wally.customersupport.agent.application.evaluation;

import java.math.BigDecimal;
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
        Map<String, Integer> failureReasons,
        Long totalTokens,
        Long providerLatencyMs,
        BigDecimal estimatedCostUsd) {

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
        if (totalTokens != null && totalTokens < 0
                || providerLatencyMs != null && providerLatencyMs < 0) {
            throw new IllegalArgumentException("operational metrics must not be negative");
        }
        if (estimatedCostUsd != null && estimatedCostUsd.signum() < 0) {
            throw new IllegalArgumentException("estimatedCostUsd must not be negative");
        }
        failureReasons = failureReasons == null ? Map.of() : Map.copyOf(failureReasons);
        estimatedCostUsd = estimatedCostUsd == null ? null : estimatedCostUsd.stripTrailingZeros();
    }

    /** Backwards-compatible constructor for callers that do not have measured provider metadata. */
    public AgentEvaluationRunSummary(
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
        this(runId, datasetVersion, agentId, agentVersion, provider, modelId, startedAt, completedAt,
                durationMs, totalScenarios, passedScenarios, failedScenarios, passRate, averageScore,
                failureReasons, null, null, null);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
