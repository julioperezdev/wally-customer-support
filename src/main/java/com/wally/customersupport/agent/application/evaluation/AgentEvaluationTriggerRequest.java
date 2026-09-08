package com.wally.customersupport.agent.application.evaluation;

/** Sanitized request metadata for a future internal evaluation trigger. */
public record AgentEvaluationTriggerRequest(
        String actorId,
        String environment,
        String capability,
        String idempotencyKey) {

    public AgentEvaluationTriggerRequest {
        actorId = optionalText(actorId);
        environment = optionalText(environment);
        capability = optionalText(capability);
        idempotencyKey = optionalText(idempotencyKey);
    }

    private static String optionalText(String value) {
        return value == null ? null : value.strip();
    }
}
