package com.wally.customersupport.agent.application.registry;

import java.time.Instant;

public record AgentRegistryAuditView(
        String operation,
        String agentId,
        Integer agentVersion,
        String previousState,
        String resultingState,
        String environment,
        String channel,
        String useCase,
        String actorId,
        String reason,
        Instant occurredAt) {
}
