package com.wally.customersupport.agent.application.evaluation;

/** Outcome of attempting to execute an internal evaluation trigger. */
public enum AgentEvaluationTriggerExecutionStatus {
    DENIED,
    ALREADY_PROCESSED,
    COMPLETED,
    FAILED
}
