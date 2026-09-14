package com.wally.customersupport.agent.application.registry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AgentExecutionTraceView(
        UUID traceId,
        String correlationId,
        String actorKey,
        String agentId,
        Integer agentVersion,
        String environment,
        String channel,
        String useCase,
        String outcome,
        String resolutionStatus,
        String provider,
        String modelId,
        long durationMs,
        Long inputTokens,
        Long outputTokens,
        BigDecimal estimatedCostUsd,
        String errorType,
        Instant executedAt) {
}
