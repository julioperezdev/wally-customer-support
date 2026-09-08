package com.wally.customersupport.agent.application.evaluation;

import java.util.Objects;

/** Sanitized access decision; it never contains credentials or tokens. */
public record AgentEvaluationControlPlaneAccessDecision(
        AgentEvaluationControlPlaneAccessStatus status,
        String actorId,
        String environment,
        String capability,
        AgentEvaluationControlPlaneAccessReason reason) {

    public AgentEvaluationControlPlaneAccessDecision {
        status = Objects.requireNonNull(status, "status");
        reason = Objects.requireNonNull(reason, "reason");
        actorId = optionalText(actorId);
        environment = optionalText(environment);
        capability = optionalText(capability);
        if (status == AgentEvaluationControlPlaneAccessStatus.AUTHORIZED
                && reason != AgentEvaluationControlPlaneAccessReason.AUTHORIZED) {
            throw new IllegalArgumentException("authorized decision must have reason AUTHORIZED");
        }
        if (status == AgentEvaluationControlPlaneAccessStatus.DENIED
                && reason == AgentEvaluationControlPlaneAccessReason.AUTHORIZED) {
            throw new IllegalArgumentException("denied decision must have a denial reason");
        }
    }

    public boolean authorized() {
        return status == AgentEvaluationControlPlaneAccessStatus.AUTHORIZED;
    }

    private static String optionalText(String value) {
        return value == null ? null : value.strip();
    }
}
