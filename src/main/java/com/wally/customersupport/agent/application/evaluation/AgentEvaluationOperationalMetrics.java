package com.wally.customersupport.agent.application.evaluation;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import com.wally.customersupport.agent.domain.model.AgentEvaluationExecutionMetadata;
import com.wally.customersupport.agent.domain.model.AgentEvaluationResult;

/** Aggregated operational metrics exposed by the sanitized evaluation contract. */
public record AgentEvaluationOperationalMetrics(
        Long totalTokens,
        Long providerLatencyMs,
        BigDecimal estimatedCostUsd) {

    public static AgentEvaluationOperationalMetrics from(List<AgentEvaluationResult> results) {
        List<AgentEvaluationResult> safeResults = results == null ? List.of() : results;
        return new AgentEvaluationOperationalMetrics(
                sumLong(safeResults, AgentEvaluationExecutionMetadata::totalTokens),
                sumLong(safeResults, AgentEvaluationExecutionMetadata::providerLatencyMs),
                sumCost(safeResults));
    }

    private static Long sumLong(
            List<AgentEvaluationResult> results,
            Function<AgentEvaluationExecutionMetadata, ? extends Number> value) {
        if (results.isEmpty() || results.stream().anyMatch(result -> result.executionMetadata() == null
                || value.apply(result.executionMetadata()) == null)) {
            return null;
        }
        return results.stream()
                .map(AgentEvaluationResult::executionMetadata)
                .map(value)
                .mapToLong(Number::longValue)
                .sum();
    }

    private static BigDecimal sumCost(List<AgentEvaluationResult> results) {
        if (results.isEmpty() || results.stream().anyMatch(result -> result.executionMetadata() == null
                || result.executionMetadata().estimatedCostUsd() == null)) {
            return null;
        }
        return results.stream()
                .map(AgentEvaluationResult::executionMetadata)
                .map(AgentEvaluationExecutionMetadata::estimatedCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .stripTrailingZeros();
    }
}
