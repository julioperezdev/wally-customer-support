package com.wally.customersupport.agent.application.shadow;

/** Decision of the quality gate; it never activates or publishes an agent. */
public enum AgentShadowQualityGateDecision {
    APPROVE_FOR_REVIEW,
    BLOCK,
    INSUFFICIENT_EVIDENCE
}
