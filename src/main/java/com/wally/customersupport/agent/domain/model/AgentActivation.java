package com.wally.customersupport.agent.domain.model;

import java.time.Instant;
import java.util.Objects;

public record AgentActivation(
        String agentId,
        int agentVersion,
        String environment,
        String channel,
        String useCase,
        String reason,
        int rolloutPercentage,
        boolean enabled,
        boolean killSwitch,
        Integer previousVersion,
        Instant activatedAt,
        String activatedBy) {

    public AgentActivation {
        agentId = required(agentId, "agentId");
        environment = required(environment, "environment");
        channel = required(channel, "channel");
        useCase = required(useCase, "useCase");
        reason = required(reason, "reason");
        activatedAt = Objects.requireNonNull(activatedAt, "activatedAt");
        activatedBy = required(activatedBy, "activatedBy");
        if (agentVersion < 1) {
            throw new IllegalArgumentException("agentVersion must be positive");
        }
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException("rolloutPercentage must be between 0 and 100");
        }
        if (enabled && (killSwitch || rolloutPercentage == 0)) {
            throw new IllegalArgumentException("an enabled activation cannot be killed or have zero rollout");
        }
        if (killSwitch && enabled) {
            throw new IllegalArgumentException("kill switch activation must be disabled");
        }
        if (previousVersion != null && (previousVersion < 1 || previousVersion == agentVersion)) {
            throw new IllegalArgumentException("previousVersion must be positive and different from agentVersion");
        }
    }

    public AgentActivation killSwitch(String actor, Instant at) {
        return new AgentActivation(
                agentId,
                agentVersion,
                environment,
                channel,
                useCase,
                "kill-switch",
                0,
                false,
                true,
                previousVersion,
                Objects.requireNonNull(at, "at"),
                actor);
    }

    public AgentActivation rollbackTo(AgentVersion previous, String actor, Instant at) {
        Objects.requireNonNull(previous, "previous");
        if (!agentId.equals(previous.agentId())) {
            throw new IllegalArgumentException("rollback target belongs to another agent");
        }
        if (previousVersion == null || previousVersion != previous.version()) {
            throw new IllegalStateException("activation has no matching previous version");
        }
        if (previous.state() != AgentLifecycleState.ACTIVE
                && previous.state() != AgentLifecycleState.APPROVED) {
            throw new IllegalStateException("rollback target must be approved or active");
        }
        return new AgentActivation(
                agentId,
                previous.version(),
                environment,
                channel,
                useCase,
                "rollback",
                100,
                true,
                false,
                agentVersion,
                Objects.requireNonNull(at, "at"),
                actor);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
