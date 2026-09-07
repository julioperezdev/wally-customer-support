package com.wally.customersupport.agent.application.service;

import java.util.Objects;

public record AgentActivationResolution(
        AgentResolutionStatus status,
        AgentResolutionReason reason,
        String agentId,
        Integer agentVersion) {

    public AgentActivationResolution {
        status = Objects.requireNonNull(status, "status");
        reason = Objects.requireNonNull(reason, "reason");
        agentId = normalize(agentId);
        if (status == AgentResolutionStatus.ACTIVE) {
            if (reason != AgentResolutionReason.ACTIVE || agentId == null || agentVersion == null) {
                throw new IllegalArgumentException("active resolution requires an agent reference");
            }
            if (agentVersion < 1) {
                throw new IllegalArgumentException("agentVersion must be positive");
            }
        } else if (agentId != null || agentVersion != null) {
            throw new IllegalArgumentException("fallback resolution must not expose an agent reference");
        }
    }

    public static AgentActivationResolution active(String agentId, int agentVersion) {
        return new AgentActivationResolution(
                AgentResolutionStatus.ACTIVE,
                AgentResolutionReason.ACTIVE,
                agentId,
                agentVersion);
    }

    public static AgentActivationResolution fallback(AgentResolutionReason reason) {
        if (reason == AgentResolutionReason.ACTIVE) {
            throw new IllegalArgumentException("active is not a fallback reason");
        }
        return new AgentActivationResolution(
                AgentResolutionStatus.FALLBACK,
                reason,
                null,
                null);
    }

    public boolean isActive() {
        return status == AgentResolutionStatus.ACTIVE;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
