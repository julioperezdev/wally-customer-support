package com.wally.customersupport.backoffice.application.model;

import java.util.Objects;

/** Read-only filters for the operational agent map. */
public record BackofficeAgentMapQuery(
        String environment,
        String channel,
        String useCase,
        String agentId) {

    public BackofficeAgentMapQuery {
        environment = required(environment, "environment");
        channel = optional(channel);
        useCase = optional(useCase);
        agentId = optional(agentId);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String optional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isBlank() ? null : normalized;
    }
}
