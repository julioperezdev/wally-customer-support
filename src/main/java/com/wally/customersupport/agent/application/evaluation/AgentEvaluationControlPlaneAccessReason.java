package com.wally.customersupport.agent.application.evaluation;

/** Sanitized reason for a control-plane authorization outcome. */
public enum AgentEvaluationControlPlaneAccessReason {
    AUTHORIZED,
    MISSING_ACTOR,
    MISSING_ENVIRONMENT,
    MISSING_CAPABILITY,
    CAPABILITY_NOT_ALLOWED,
    ENVIRONMENT_NOT_ALLOWED,
    AUTHORIZER_DENIED
}
