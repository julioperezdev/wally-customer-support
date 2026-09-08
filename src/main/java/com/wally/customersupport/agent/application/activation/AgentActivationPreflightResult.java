package com.wally.customersupport.agent.application.activation;

import java.util.List;

/** Provider-neutral, content-free preflight contract. */
public record AgentActivationPreflightResult(
        AgentActivationPreflightStatus status,
        boolean canActivate,
        String agentId,
        Integer agentVersion,
        String environment,
        String channel,
        String useCase,
        List<AgentActivationPreflightCheck> checks) {

    public AgentActivationPreflightResult {
        checks = List.copyOf(checks);
    }
}
