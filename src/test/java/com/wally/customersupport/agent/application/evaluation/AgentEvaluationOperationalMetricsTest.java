package com.wally.customersupport.agent.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wally.customersupport.agent.domain.model.AgentEvaluationExecutionMetadata;
import com.wally.customersupport.agent.domain.model.AgentEvaluationResult;

class AgentEvaluationOperationalMetricsTest {

    @Test
    void aggregatesMeasuredMetricsWithoutResponseContent() {
        var metrics = AgentEvaluationOperationalMetrics.from(List.of(
                result(new AgentEvaluationExecutionMetadata(
                        "agent", "v1", "bedrock", "model-a", 100, 80L, 10, 4, 14,
                        new BigDecimal("0.0012"), "pricing-v1")),
                result(new AgentEvaluationExecutionMetadata(
                        "agent", "v1", "bedrock", "model-a", 120, 90L, 12, 5, 17,
                        new BigDecimal("0.0023"), "pricing-v1"))));

        assertThat(metrics.totalTokens()).isEqualTo(31L);
        assertThat(metrics.providerLatencyMs()).isEqualTo(170L);
        assertThat(metrics.estimatedCostUsd()).isEqualByComparingTo("0.0035");
    }

    @Test
    void leavesEachMetricUnknownWhenProviderDidNotReturnIt() {
        var metrics = AgentEvaluationOperationalMetrics.from(List.of(
                result(new AgentEvaluationExecutionMetadata(
                        "agent", "v1", "mock", "model", 10, null, null, null, null, null, null))));

        assertThat(metrics.totalTokens()).isNull();
        assertThat(metrics.providerLatencyMs()).isNull();
        assertThat(metrics.estimatedCostUsd()).isNull();
    }

    private static AgentEvaluationResult result(AgentEvaluationExecutionMetadata metadata) {
        return new AgentEvaluationResult("scenario", "dataset-v1", true, 1.0, List.of(), metadata);
    }
}
