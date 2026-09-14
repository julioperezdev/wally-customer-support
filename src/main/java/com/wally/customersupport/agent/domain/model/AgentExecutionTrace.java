package com.wally.customersupport.agent.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Sanitized production execution evidence for one resolved agent route.
 *
 * <p>It contains no message text or direct customer identifier. Correlation
 * and actor values are pseudonymous values already produced by WCS.</p>
 */
public record AgentExecutionTrace(
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

    public AgentExecutionTrace {
        traceId = Objects.requireNonNull(traceId, "traceId");
        correlationId = optional(correlationId);
        actorKey = optional(actorKey);
        agentId = optional(agentId);
        environment = required(environment, "environment");
        channel = required(channel, "channel");
        useCase = required(useCase, "useCase");
        outcome = required(outcome, "outcome");
        resolutionStatus = required(resolutionStatus, "resolutionStatus");
        provider = optional(provider);
        modelId = optional(modelId);
        errorType = optional(errorType);
        executedAt = Objects.requireNonNull(executedAt, "executedAt");
        if (agentVersion != null && agentVersion < 1) {
            throw new IllegalArgumentException("agentVersion must be positive");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        if (inputTokens != null && inputTokens < 0 || outputTokens != null && outputTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
        if (estimatedCostUsd != null && estimatedCostUsd.signum() < 0) {
            throw new IllegalArgumentException("estimatedCostUsd must not be negative");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
