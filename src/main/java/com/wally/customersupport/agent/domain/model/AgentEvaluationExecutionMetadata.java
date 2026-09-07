package com.wally.customersupport.agent.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/** Safe operational metadata for one evaluation execution; it contains no response text. */
public record AgentEvaluationExecutionMetadata(
        String agentId,
        String agentVersion,
        String provider,
        String modelId,
        long durationMs,
        Long providerLatencyMs,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        BigDecimal estimatedCostUsd,
        String pricingVersion) {

    public AgentEvaluationExecutionMetadata {
        agentId = normalize(agentId);
        agentVersion = normalize(agentVersion);
        provider = normalize(provider);
        modelId = normalize(modelId);
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        providerLatencyMs = nonNegative(providerLatencyMs, "providerLatencyMs");
        inputTokens = nonNegative(inputTokens, "inputTokens");
        outputTokens = nonNegative(outputTokens, "outputTokens");
        totalTokens = nonNegative(totalTokens, "totalTokens");
        if (estimatedCostUsd != null && estimatedCostUsd.signum() < 0) {
            throw new IllegalArgumentException("estimatedCostUsd must not be negative");
        }
        estimatedCostUsd = estimatedCostUsd == null ? null : estimatedCostUsd.stripTrailingZeros();
        pricingVersion = normalize(pricingVersion);
    }

    private static <T extends Number> T nonNegative(T value, String field) {
        if (value == null) {
            return null;
        }
        if (value.longValue() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Objects.requireNonNull(value).strip();
        return normalized.isBlank() ? null : normalized;
    }
}
