package com.wally.customersupport.agent.application.evaluation;

/** Sanitized reason explaining a retention activation gate result. */
public enum AgentEvaluationRetentionActivationReason {
    NONE,
    MISSING_ENVIRONMENT,
    MISSING_APPROVER,
    MISSING_APPROVAL_REFERENCE,
    MISSING_APPROVED_AT,
    APPROVAL_IN_FUTURE
}
