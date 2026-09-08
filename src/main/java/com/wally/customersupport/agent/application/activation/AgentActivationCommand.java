package com.wally.customersupport.agent.application.activation;

import java.util.Objects;

import com.wally.customersupport.agent.domain.model.AgentActivationRequest;

/** Sanitized command to activate an approved agent version. */
public record AgentActivationCommand(
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

    public AgentActivationCommand {
        agentId = required(agentId, "agentId");
        environment = required(environment, "environment");
        channel = required(channel, "channel");
        useCase = required(useCase, "useCase");
        reason = required(reason, "reason");
        approvalReference = required(approvalReference, "approvalReference");
        operationalApprovalReference = required(operationalApprovalReference, "operationalApprovalReference");
        if (agentVersion < 1) {
            throw new IllegalArgumentException("agentVersion must be positive");
        }
    }

    public AgentActivationRequest toDomainRequest() {
        return new AgentActivationRequest(
                environment,
                channel,
                useCase,
                reason,
                rolloutPercentage,
                enabled);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
