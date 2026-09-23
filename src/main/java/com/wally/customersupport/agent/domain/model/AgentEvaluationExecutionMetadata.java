package com.wally.customersupport.agent.domain.model;

import java.math.BigDecimal;
import java.util.List;
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
        String pricingVersion,
        String routedIntent,
        String routedAction,
        List<String> resolvedEntityTypes,
        String toolName,
        Boolean toolSucceeded,
        Boolean grounded,
        Integer routedQuantity) {

    /** Backwards-compatible operational metadata without quality signals. */
    public AgentEvaluationExecutionMetadata(
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
        this(agentId, agentVersion, provider, modelId, durationMs, providerLatencyMs,
                inputTokens, outputTokens, totalTokens, estimatedCostUsd, pricingVersion,
                null, null, null, null, null, null);
    }

    /** Backwards-compatible quality metadata contract before quantity accuracy was measured. */
    public AgentEvaluationExecutionMetadata(
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
            String pricingVersion,
            String routedIntent,
            String routedAction,
            List<String> resolvedEntityTypes,
            String toolName,
            Boolean toolSucceeded,
            Boolean grounded) {
        this(agentId, agentVersion, provider, modelId, durationMs, providerLatencyMs,
                inputTokens, outputTokens, totalTokens, estimatedCostUsd, pricingVersion,
                routedIntent, routedAction, resolvedEntityTypes, toolName, toolSucceeded, grounded, null);
    }

    /** Backwards-compatible quality metadata contract before action accuracy was measured. */
    public AgentEvaluationExecutionMetadata(
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
            String pricingVersion,
            String routedIntent,
            List<String> resolvedEntityTypes,
            String toolName,
            Boolean toolSucceeded,
            Boolean grounded) {
        this(agentId, agentVersion, provider, modelId, durationMs, providerLatencyMs,
                inputTokens, outputTokens, totalTokens, estimatedCostUsd, pricingVersion,
                routedIntent, null, resolvedEntityTypes, toolName, toolSucceeded, grounded, null);
    }

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
        routedIntent = normalize(routedIntent);
        routedAction = normalize(routedAction);
        resolvedEntityTypes = resolvedEntityTypes == null
                ? null
                : resolvedEntityTypes.stream()
                        .filter(Objects::nonNull)
                        .map(String::strip)
                        .filter(value -> !value.isBlank())
                        .distinct()
                        .sorted()
                        .toList();
        toolName = normalize(toolName);
        if (routedQuantity != null && (routedQuantity < 1 || routedQuantity > 100)) {
            throw new IllegalArgumentException("routedQuantity must be between 1 and 100");
        }
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
