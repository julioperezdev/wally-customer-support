package com.wally.customersupport.agent.application.evaluation;

/** Sanitized reason for an internal evaluation trigger outcome. */
public enum AgentEvaluationTriggerExecutionReason {
    AUTHORIZATION_DENIED,
    IDEMPOTENCY_ALREADY_CLAIMED,
    IDEMPOTENCY_GUARD_FAILED,
    EVALUATION_COMPLETED,
    EVALUATION_FAILED
}
