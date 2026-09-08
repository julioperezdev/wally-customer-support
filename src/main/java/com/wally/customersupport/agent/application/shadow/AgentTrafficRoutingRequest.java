package com.wally.customersupport.agent.application.shadow;

import java.util.Objects;

/** Input for deterministic candidate routing; conversation keys must already be pseudonymized. */
public record AgentTrafficRoutingRequest(
        AgentTrafficMode mode,
        int rolloutPercentage,
        String pseudonymizedConversationKey) {

    public AgentTrafficRoutingRequest {
        mode = Objects.requireNonNull(mode, "mode");
        pseudonymizedConversationKey = required(pseudonymizedConversationKey, "pseudonymizedConversationKey");
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException("rolloutPercentage must be between 0 and 100");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
