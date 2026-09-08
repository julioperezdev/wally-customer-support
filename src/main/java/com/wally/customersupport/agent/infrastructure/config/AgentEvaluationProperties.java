package com.wally.customersupport.agent.infrastructure.config;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** External guardrails for bounded, provider-backed evaluation runs. */
@ConfigurationProperties(prefix = "wcs.agent-evaluation")
public record AgentEvaluationProperties(
        String executor,
        Integer maxScenarios,
        Integer maxInputTokensPerScenario,
        Integer maxOutputTokens,
        BigDecimal maxEstimatedCostUsd,
        Duration timeout) {

    public String effectiveExecutor() {
        return executor == null || executor.isBlank() ? "deterministic" : executor.trim().toLowerCase();
    }

    public int effectiveMaxScenarios() {
        return positiveOrDefault(maxScenarios, 10);
    }

    public int effectiveMaxInputTokensPerScenario() {
        return positiveOrDefault(maxInputTokensPerScenario, 4_000);
    }

    public int effectiveMaxOutputTokens() {
        return positiveOrDefault(maxOutputTokens, 512);
    }

    public BigDecimal effectiveMaxEstimatedCostUsd() {
        return maxEstimatedCostUsd == null || maxEstimatedCostUsd.signum() <= 0
                ? new BigDecimal("0.0500")
                : maxEstimatedCostUsd;
    }

    public Duration effectiveTimeout() {
        return timeout == null || timeout.isNegative() || timeout.isZero()
                ? Duration.ofSeconds(30)
                : timeout;
    }

    private static int positiveOrDefault(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }
}
