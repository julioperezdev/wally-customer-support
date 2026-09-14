package com.wally.customersupport.agent.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Content-free audit evidence for changes to the agent control plane.
 *
 * <p>Approval values, prompts and conversation content are intentionally not
 * stored here. The event records who changed what, why and where.</p>
 */
public record AgentRegistryAuditEvent(
        String operation,
        String agentId,
        Integer agentVersion,
        String previousState,
        String resultingState,
        String environment,
        String channel,
        String useCase,
        String actorId,
        String reason,
        Instant occurredAt) {

    public AgentRegistryAuditEvent {
        operation = required(operation, "operation");
        agentId = required(agentId, "agentId");
        previousState = optional(previousState);
        resultingState = optional(resultingState);
        environment = optional(environment);
        channel = optional(channel);
        useCase = optional(useCase);
        actorId = required(actorId, "actorId");
        reason = required(reason, "reason");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        if (agentVersion != null && agentVersion < 1) {
            throw new IllegalArgumentException("agentVersion must be positive");
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
