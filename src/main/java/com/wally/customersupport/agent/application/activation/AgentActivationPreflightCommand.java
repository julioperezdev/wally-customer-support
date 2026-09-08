package com.wally.customersupport.agent.application.activation;

import java.util.Objects;

import com.wally.customersupport.agent.domain.model.AgentActivationRequest;

/** Non-mutating activation input; approval references are optional so the preflight can report missing checks. */
public record AgentActivationPreflightCommand(
        String agentId,
        int agentVersion,
        String environment,
        String channel,
        String useCase,
        String reason,
        int rolloutPercentage,
        boolean enabled,
        String approvalReference,
        String operationalApprovalReference) {

    public AgentActivationPreflightCommand {
        agentId = required(agentId, "agentId");
        environment = required(environment, "environment");
        channel = required(channel, "channel");
        useCase = required(useCase, "useCase");
        reason = required(reason, "reason");
        approvalReference = normalize(approvalReference);
        operationalApprovalReference = normalize(operationalApprovalReference);
        if (agentVersion < 1) {
            throw new IllegalArgumentException("agentVersion must be positive");
        }
    }

    public AgentActivationRequest toDomainRequest() {
        return new AgentActivationRequest(environment, channel, useCase, reason, rolloutPercentage, enabled);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
