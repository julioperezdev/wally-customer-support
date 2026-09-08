package com.wally.customersupport.shared.infrastructure.config;

import java.math.BigDecimal;

import com.wally.customersupport.agent.application.shadow.AgentShadowQualityGatePolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** AppConfig-backed limits for reviewing shadow evidence. */
@ConfigurationProperties(prefix = "wcs.agent-runtime.shadow-quality")
public record AgentShadowQualityProperties(
        Integer minimumSampleSize,
        Double maximumFailureRate,
        Double maximumMismatchRate,
        Double maximumUnknownRate,
        Long maximumLatencyP95Ms,
        BigDecimal maximumAverageCostUsd) {

    public AgentShadowQualityGatePolicy effectivePolicy() {
        AgentShadowQualityGatePolicy defaults = AgentShadowQualityGatePolicy.safeDefault();
        return new AgentShadowQualityGatePolicy(
                positiveOrDefault(minimumSampleSize, defaults.minimumSampleSize()),
                rateOrDefault(maximumFailureRate, defaults.maximumFailureRate()),
                rateOrDefault(maximumMismatchRate, defaults.maximumMismatchRate()),
                rateOrDefault(maximumUnknownRate, defaults.maximumUnknownRate()),
                positiveOrDefault(maximumLatencyP95Ms, defaults.maximumLatencyP95Ms()),
                positiveCostOrDefault(maximumAverageCostUsd, defaults.maximumAverageCostUsd()));
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }

    private static long positiveOrDefault(Long value, long fallback) {
        return value == null || value < 1 ? fallback : value;
    }

    private static double rateOrDefault(Double value, double fallback) {
        return value == null || value.isNaN() || value.isInfinite() || value < 0 || value > 1
                ? fallback
                : value;
    }

    private static BigDecimal positiveCostOrDefault(BigDecimal value, BigDecimal fallback) {
        return value == null || value.signum() < 0 ? fallback : value;
    }
}
