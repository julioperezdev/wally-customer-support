package com.wally.customersupport.agent.application.evaluation;

/** Sanitized reason for an evaluation trigger authorization outcome. */
public enum AgentEvaluationTriggerAuthorizationReason {
    AUTHORIZED,
    MISSING_ACTOR,
    MISSING_ENVIRONMENT,
    MISSING_CAPABILITY,
    MISSING_IDEMPOTENCY_KEY,
    CAPABILITY_NOT_ALLOWED,
    ENVIRONMENT_NOT_ALLOWED,
    AUTHORIZER_DENIED
}
