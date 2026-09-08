package com.wally.customersupport.agent.application.activation;

import java.time.Instant;

/** Sanitized result for a control-plane mutation; it never exposes actor or approval contents. */
public record AgentActivationMutationResult(
        AgentActivationMutationStatus status,
        AgentActivationMutationReason reason,
        String agentId,
        Integer agentVersion,
        String environment,
        String channel,
        String useCase,
        Instant activatedAt) {

    public boolean successful() {
        return status == AgentActivationMutationStatus.ACTIVATED
                || status == AgentActivationMutationStatus.KILL_SWITCHED
                || status == AgentActivationMutationStatus.ROLLED_BACK;
    }
}
