package com.wally.customersupport.agent.infrastructure.http;

import java.util.List;

import com.wally.customersupport.agent.application.activation.AgentActivationPreflightCheck;
import com.wally.customersupport.agent.application.activation.AgentActivationPreflightResult;

/** HTTP representation of the non-mutating activation preflight. */
public record AgentActivationPreflightHttpResponse(
        String status,
        boolean canActivate,
        String agentId,
        Integer agentVersion,
        String environment,
        String channel,
        String useCase,
        List<AgentActivationPreflightCheck> checks) {

    static AgentActivationPreflightHttpResponse from(AgentActivationPreflightResult result) {
        return new AgentActivationPreflightHttpResponse(
                result.status().name(),
                result.canActivate(),
                result.agentId(),
                result.agentVersion(),
                result.environment(),
                result.channel(),
                result.useCase(),
                result.checks());
    }
}
