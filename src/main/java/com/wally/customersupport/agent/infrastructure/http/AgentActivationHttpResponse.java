package com.wally.customersupport.agent.infrastructure.http;

import java.time.Instant;

import com.wally.customersupport.agent.application.activation.AgentActivationMutationResult;

/** Sanitized HTTP output for an activation mutation. */
public record AgentActivationHttpResponse(
        String status,
        String reason,
        String agentId,
        Integer agentVersion,
        String environment,
        String channel,
        String useCase,
        Instant activatedAt) {

    static AgentActivationHttpResponse from(AgentActivationMutationResult result) {
        return new AgentActivationHttpResponse(
                result.status().name(),
                result.reason().name(),
                result.agentId(),
                result.agentVersion(),
                result.environment(),
                result.channel(),
                result.useCase(),
                result.activatedAt());
    }
}
