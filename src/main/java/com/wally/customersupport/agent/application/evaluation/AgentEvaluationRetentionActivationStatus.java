package com.wally.customersupport.agent.application.evaluation;

/** Result of the explicit retention activation gate, not a destructive action. */
public enum AgentEvaluationRetentionActivationStatus {
    NOT_REQUESTED,
    REJECTED,
    APPROVED_FOR_REVIEW
}
