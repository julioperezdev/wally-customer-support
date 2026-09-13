package com.wally.customersupport.agent.application.registry;

import java.time.Instant;

import com.wally.customersupport.agent.domain.model.AgentLifecycleState;

/** Content-free result for authoring and lifecycle commands. */
public record AgentRegistryMutationResult(
        AgentRegistryMutationStatus status,
        AgentRegistryMutationReason reason,
        String agentId,
        Integer version,
        AgentLifecycleState state,
        Instant createdAt,
        Instant changedAt) {
}
