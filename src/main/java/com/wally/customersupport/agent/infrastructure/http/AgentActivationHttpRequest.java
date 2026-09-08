package com.wally.customersupport.agent.infrastructure.http;

import com.wally.customersupport.agent.application.activation.AgentActivationCommand;
import com.wally.customersupport.agent.application.activation.AgentActivationPreflightCommand;

/** HTTP input for activation; actor and environment authority come from the server boundary. */
public record AgentActivationHttpRequest(
        String agentId,
        int agentVersion,
        String environment,
        String channel,
        String useCase,
        String reason,
        int rolloutPercentage,
        boolean enabled,
        String approvalReference,
        String operationalApprovalReference) {

    AgentActivationCommand toCommand() {
        return new AgentActivationCommand(
                agentId,
                agentVersion,
                environment,
                channel,
                useCase,
                reason,
                rolloutPercentage,
                enabled,
                approvalReference,
                operationalApprovalReference);
    }

    AgentActivationPreflightCommand toPreflightCommand() {
        return new AgentActivationPreflightCommand(
                agentId,
                agentVersion,
                environment,
                channel,
                useCase,
                reason,
                rolloutPercentage,
                enabled,
                approvalReference,
                operationalApprovalReference);
    }
}
