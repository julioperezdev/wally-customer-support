package com.wally.customersupport.agent.application.service;

import java.util.Objects;

public record AgentRuntimeDefinitionResolution(
        AgentDefinitionResolutionStatus status,
        AgentDefinitionResolutionReason reason,
        AgentRuntimeDefinition definition) {

    public AgentRuntimeDefinitionResolution {
        status = Objects.requireNonNull(status, "status");
        reason = Objects.requireNonNull(reason, "reason");
        if (status == AgentDefinitionResolutionStatus.ACTIVE) {
            if (reason != AgentDefinitionResolutionReason.ACTIVE || definition == null) {
                throw new IllegalArgumentException("active resolution requires an executable definition");
            }
        } else if (reason == AgentDefinitionResolutionReason.ACTIVE || definition != null) {
            throw new IllegalArgumentException("fallback resolution must not expose an executable definition");
        }
    }

    public static AgentRuntimeDefinitionResolution active(AgentRuntimeDefinition definition) {
        return new AgentRuntimeDefinitionResolution(
                AgentDefinitionResolutionStatus.ACTIVE,
                AgentDefinitionResolutionReason.ACTIVE,
                definition);
    }

    public static AgentRuntimeDefinitionResolution fallback(AgentDefinitionResolutionReason reason) {
        if (reason == AgentDefinitionResolutionReason.ACTIVE) {
            throw new IllegalArgumentException("active is not a fallback reason");
        }
        return new AgentRuntimeDefinitionResolution(
                AgentDefinitionResolutionStatus.FALLBACK,
                reason,
                null);
    }

    public boolean isActive() {
        return status == AgentDefinitionResolutionStatus.ACTIVE;
    }
}
