package com.wally.customersupport.agent.application.evaluation;

/** Sanitized request metadata used by the evaluation control-plane boundary. */
public record AgentEvaluationControlPlaneAccessRequest(
        String actorId,
        String environment,
        String capability) {

    public AgentEvaluationControlPlaneAccessRequest {
        actorId = optionalText(actorId);
        environment = optionalText(environment);
        capability = optionalText(capability);
    }

    private static String optionalText(String value) {
        return value == null ? null : value.strip();
    }
}
