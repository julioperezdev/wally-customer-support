package com.wally.customersupport.agent.application.activation;

import java.util.Objects;

/** Sanitized command for an auditable kill-switch or rollback action. */
public record AgentActivationActionCommand(
        String agentId,
        String environment,
        String channel,
        String useCase,
        String approvalReference,
        String operationalApprovalReference) {

    public AgentActivationActionCommand {
        agentId = required(agentId, "agentId");
        environment = required(environment, "environment");
        channel = required(channel, "channel");
        useCase = required(useCase, "useCase");
        approvalReference = required(approvalReference, "approvalReference");
        operationalApprovalReference = required(operationalApprovalReference, "operationalApprovalReference");
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
