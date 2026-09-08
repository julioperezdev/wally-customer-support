package com.wally.customersupport.agent.application.shadow;

import java.math.BigDecimal;

/**
 * Sanitized candidate outcome. It intentionally has no candidate response;
 * shadow output can never reach an outbound channel through this contract.
 */
public record AgentShadowExecutionResult(
        String outcome,
        long latencyMs,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        BigDecimal estimatedCostUsd,
        String fallbackReason) {

    public AgentShadowExecutionResult {
        if (outcome == null || outcome.isBlank()) {
            throw new IllegalArgumentException("outcome must not be blank");
        }
        if (latencyMs < 0) {
            throw new IllegalArgumentException("latencyMs must not be negative");
        }
        validateNonNegative(inputTokens, "inputTokens");
        validateNonNegative(outputTokens, "outputTokens");
        validateNonNegative(totalTokens, "totalTokens");
        if (estimatedCostUsd != null && estimatedCostUsd.signum() < 0) {
            throw new IllegalArgumentException("estimatedCostUsd must not be negative");
        }
    }

    public static AgentShadowExecutionResult disabled(String reason) {
        return new AgentShadowExecutionResult("DISABLED", 0, null, null, null, null, reason);
    }

    public static AgentShadowExecutionResult skipped(String reason) {
        return new AgentShadowExecutionResult("SKIPPED", 0, null, null, null, null, reason);
    }

    public static AgentShadowExecutionResult failed(long latencyMs, String reason) {
        return new AgentShadowExecutionResult("FAILED", latencyMs, null, null, null, null, reason);
    }

    public AgentShadowExecutionResult withLimitOutcome(String reason) {
        return new AgentShadowExecutionResult(
                "LIMIT_EXCEEDED",
                latencyMs,
                inputTokens,
                outputTokens,
                totalTokens,
                estimatedCostUsd,
                reason);
    }

    private static void validateNonNegative(Integer value, String field) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
    }
}
