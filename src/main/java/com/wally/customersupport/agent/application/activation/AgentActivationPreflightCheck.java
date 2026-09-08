package com.wally.customersupport.agent.application.activation;

/** Sanitized result of one non-mutating activation validation. */
public record AgentActivationPreflightCheck(
        String code,
        AgentActivationPreflightCheckStatus status,
        String message) {
}
