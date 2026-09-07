package com.wally.customersupport.agent.domain.model;

import java.util.Objects;

public record AgentActivationRequest(
        String environment,
        String channel,
        String useCase,
        String reason,
        int rolloutPercentage,
        boolean enabled) {

    public AgentActivationRequest {
        environment = required(environment, "environment");
        channel = required(channel, "channel");
        useCase = required(useCase, "useCase");
        reason = required(reason, "reason");
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException("rolloutPercentage must be between 0 and 100");
        }
        if (enabled && rolloutPercentage == 0) {
            throw new IllegalArgumentException("enabled activation must have a rollout percentage");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
