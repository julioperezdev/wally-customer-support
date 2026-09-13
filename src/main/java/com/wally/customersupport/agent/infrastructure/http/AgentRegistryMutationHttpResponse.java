package com.wally.customersupport.agent.infrastructure.http;

import java.time.Instant;

import com.wally.customersupport.agent.application.registry.AgentRegistryMutationResult;

/** Sanitized authoring result; no prompt, token or approval value is returned. */
public record AgentRegistryMutationHttpResponse(
        String status,
        String reason,
        String agentId,
        Integer version,
        String state,
        Instant createdAt,
        Instant changedAt) {

    static AgentRegistryMutationHttpResponse from(AgentRegistryMutationResult result) {
        return new AgentRegistryMutationHttpResponse(
                result.status().name(),
                result.reason().name(),
                result.agentId(),
                result.version(),
                result.state() == null ? null : result.state().name(),
                result.createdAt(),
                result.changedAt());
    }
}
