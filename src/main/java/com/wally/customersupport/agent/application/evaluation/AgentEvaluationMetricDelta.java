package com.wally.customersupport.agent.application.evaluation;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Sanitized quality and operational deltas from baseline to candidate. */
public record AgentEvaluationMetricDelta(
        int passedScenariosDelta,
        int failedScenariosDelta,
        double passRateDelta,
        double averageScoreDelta,
        long durationMsDelta,
        OptionalLong totalTokensDelta,
        OptionalLong providerLatencyMsDelta,
        Optional<BigDecimal> estimatedCostUsdDelta) {

    public AgentEvaluationMetricDelta {
        if (Double.isNaN(passRateDelta) || Double.isNaN(averageScoreDelta)) {
            throw new IllegalArgumentException("quality deltas must be numeric");
        }
        totalTokensDelta = Objects.requireNonNull(totalTokensDelta, "totalTokensDelta");
        providerLatencyMsDelta = Objects.requireNonNull(providerLatencyMsDelta, "providerLatencyMsDelta");
        estimatedCostUsdDelta = Objects.requireNonNull(estimatedCostUsdDelta, "estimatedCostUsdDelta");
    }
}
