package com.wally.customersupport.backoffice.application.model;

import java.util.Objects;

public record BackofficeAgentMapSimulationRequest(
        String environment,
        String channel,
        String useCase,
        String disabledAgentId,
        Integer disabledVersion) {

    public BackofficeAgentMapSimulationRequest {
        environment = required(environment, "environment");
        channel = required(channel, "channel");
        useCase = required(useCase, "useCase");
        disabledAgentId = required(disabledAgentId, "disabledAgentId");
        if (disabledVersion != null && disabledVersion < 1) {
            throw new IllegalArgumentException("disabledVersion must be positive");
        }
    }

    public BackofficeAgentMapQuery toQuery() {
        return new BackofficeAgentMapQuery(environment, channel, useCase, null);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
