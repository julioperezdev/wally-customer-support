package com.wally.customersupport.agent.infrastructure.http;

/** Stable, content-free error response for the internal evaluation API. */
public record AgentEvaluationControlPlaneError(String code) {
}
