package com.wally.customersupport.agent.application.service;

import java.util.Objects;
import java.util.Set;

/** Immutable runtime policy for one bounded specialist responsibility. */
public record AgentSpecialistDefinition(
        String agentId,
        String responsibility,
        String sourcePolicy,
        Set<String> allowedTools,
        Set<String> useCases) {

    public AgentSpecialistDefinition {
        agentId = required(agentId, "agentId");
        responsibility = required(responsibility, "responsibility");
        sourcePolicy = required(sourcePolicy, "sourcePolicy");
        allowedTools = immutableSet(allowedTools, "allowedTools");
        useCases = immutableSet(useCases, "useCases");
    }

    public boolean supports(String useCase) {
        return useCase != null && useCases.contains(useCase);
    }

    public boolean allows(String toolName) {
        return toolName != null && allowedTools.contains(toolName);
    }

    private static Set<String> immutableSet(Set<String> values, String field) {
        Objects.requireNonNull(values, field);
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(field + " must not contain blank values");
        }
        return Set.copyOf(values);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
