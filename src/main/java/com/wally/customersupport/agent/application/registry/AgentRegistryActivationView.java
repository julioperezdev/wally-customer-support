package com.wally.customersupport.agent.application.registry;

import java.time.Instant;

/** Sanitized activation history; actor identities are not exposed by the read contract. */
public record AgentRegistryActivationView(
        String agentId,
        int agentVersion,
        String environment,
        String channel,
        String useCase,
        String reason,
        int rolloutPercentage,
        boolean enabled,
        boolean killSwitch,
        Integer previousVersion,
        Instant activatedAt) {
}
