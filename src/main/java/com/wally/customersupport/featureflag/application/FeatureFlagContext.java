package com.wally.customersupport.featureflag.application;

import java.util.Objects;

/** Stable dimensions used to evaluate a business flag without exposing message content. */
public record FeatureFlagContext(
        String environment,
        String channel,
        String useCase,
        String agentId,
        Integer agentVersion) {

    public FeatureFlagContext {
        environment = required(environment, "environment");
        channel = optional(channel);
        useCase = optional(useCase);
        agentId = optional(agentId);
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
        return value == null || value.isBlank() ? null : value.strip();
    }
}
