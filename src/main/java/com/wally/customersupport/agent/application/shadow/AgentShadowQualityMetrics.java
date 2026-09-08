package com.wally.customersupport.agent.application.shadow;

import java.math.BigDecimal;

/** Sanitized aggregate used by the shadow quality gate. */
public record AgentShadowQualityMetrics(
        int totalExecutions,
        int completedExecutions,
        int failedExecutions,
        int matchCount,
        int mismatchCount,
        int unknownCount,
        Long latencyP95Ms,
        BigDecimal averageCostUsd) {

    public AgentShadowQualityMetrics {
        if (totalExecutions < 0 || completedExecutions < 0 || failedExecutions < 0
                || matchCount < 0 || mismatchCount < 0 || unknownCount < 0) {
            throw new IllegalArgumentException("quality counters must not be negative");
        }
        if (latencyP95Ms != null && latencyP95Ms < 0) {
            throw new IllegalArgumentException("latencyP95Ms must not be negative");
        }
        if (averageCostUsd != null && averageCostUsd.signum() < 0) {
            throw new IllegalArgumentException("averageCostUsd must not be negative");
        }
        averageCostUsd = averageCostUsd == null ? null : averageCostUsd.stripTrailingZeros();
    }
}
