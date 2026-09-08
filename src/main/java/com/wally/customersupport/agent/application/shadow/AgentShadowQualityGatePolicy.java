package com.wally.customersupport.agent.application.shadow;

import java.math.BigDecimal;
import java.util.Objects;

/** Explicit operational limits used to review a shadow/canary candidate. */
public record AgentShadowQualityGatePolicy(
        int minimumSampleSize,
        double maximumFailureRate,
        double maximumMismatchRate,
        double maximumUnknownRate,
        long maximumLatencyP95Ms,
        BigDecimal maximumAverageCostUsd) {

    public AgentShadowQualityGatePolicy {
        if (minimumSampleSize < 1) {
            throw new IllegalArgumentException("minimumSampleSize must be positive");
        }
        validateRate(maximumFailureRate, "maximumFailureRate");
        validateRate(maximumMismatchRate, "maximumMismatchRate");
        validateRate(maximumUnknownRate, "maximumUnknownRate");
        if (maximumLatencyP95Ms < 1) {
            throw new IllegalArgumentException("maximumLatencyP95Ms must be positive");
        }
        maximumAverageCostUsd = Objects.requireNonNull(maximumAverageCostUsd, "maximumAverageCostUsd");
        if (maximumAverageCostUsd.signum() < 0) {
            throw new IllegalArgumentException("maximumAverageCostUsd must not be negative");
        }
        maximumAverageCostUsd = maximumAverageCostUsd.stripTrailingZeros();
    }

    public static AgentShadowQualityGatePolicy safeDefault() {
        return new AgentShadowQualityGatePolicy(
                20,
                0.10,
                0.15,
                0.10,
                5_000,
                new BigDecimal("0.0100"));
    }

    private static void validateRate(double value, String field) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(field + " must be between 0 and 1");
        }
    }
}
