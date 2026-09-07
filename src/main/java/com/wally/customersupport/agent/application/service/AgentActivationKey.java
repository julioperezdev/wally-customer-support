package com.wally.customersupport.agent.application.service;

import java.util.Objects;

public record AgentActivationKey(
        String agentId,
        String environment,
        String channel,
        String useCase) {

    public AgentActivationKey {
        agentId = required(agentId, "agentId");
        environment = required(environment, "environment");
        channel = required(channel, "channel");
        useCase = required(useCase, "useCase");
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
