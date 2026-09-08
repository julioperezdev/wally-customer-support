package com.wally.customersupport.agent.application.evaluation;

import java.util.Objects;

/** Sanitized authorization outcome; it never contains credentials or tokens. */
public record AgentEvaluationTriggerAuthorizationDecision(
        AgentEvaluationTriggerAuthorizationStatus status,
        String actorId,
        String environment,
        String capability,
        AgentEvaluationTriggerAuthorizationReason reason) {

    public AgentEvaluationTriggerAuthorizationDecision {
        status = Objects.requireNonNull(status, "status");
        reason = Objects.requireNonNull(reason, "reason");
        actorId = optionalText(actorId);
        environment = optionalText(environment);
        capability = optionalText(capability);
        if (status == AgentEvaluationTriggerAuthorizationStatus.AUTHORIZED
                && reason != AgentEvaluationTriggerAuthorizationReason.AUTHORIZED) {
            throw new IllegalArgumentException("authorized decision must have reason AUTHORIZED");
        }
        if (status == AgentEvaluationTriggerAuthorizationStatus.DENIED
                && reason == AgentEvaluationTriggerAuthorizationReason.AUTHORIZED) {
            throw new IllegalArgumentException("denied decision must have a denial reason");
        }
    }

    public boolean authorized() {
        return status == AgentEvaluationTriggerAuthorizationStatus.AUTHORIZED;
    }

    private static String optionalText(String value) {
        return value == null ? null : value.strip();
    }
}
