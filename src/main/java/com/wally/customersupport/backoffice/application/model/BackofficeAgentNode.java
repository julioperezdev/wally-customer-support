package com.wally.customersupport.backoffice.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BackofficeAgentNode(
        String agentId,
        int version,
        String name,
        String purpose,
        String state,
        String status,
        String modelProvider,
        String modelId,
        Instant createdAt,
        long ageDays,
        boolean enabled,
        boolean killSwitch,
        int rolloutPercentage,
        long executionCount,
        long successCount,
        long failureCount,
        double successRate,
        Long averageLatencyMs,
        Long totalTokens,
        BigDecimal estimatedCostUsd,
        String fallbackAgentId,
        List<String> allowedTools,
        List<String> knowledgeSources,
        String metricsSource) {

    public BackofficeAgentNode {
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        knowledgeSources = knowledgeSources == null ? List.of() : List.copyOf(knowledgeSources);
    }
}
