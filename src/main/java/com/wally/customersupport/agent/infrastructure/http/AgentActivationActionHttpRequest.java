package com.wally.customersupport.agent.infrastructure.http;

import com.wally.customersupport.agent.application.activation.AgentActivationActionCommand;

/** HTTP input for kill-switch and rollback actions. */
public record AgentActivationActionHttpRequest(
        String agentId,
        String environment,
        String channel,
        String useCase,
        String approvalReference,
        String operationalApprovalReference) {

    AgentActivationActionCommand toCommand() {
        return new AgentActivationActionCommand(
                agentId,
                environment,
                channel,
                useCase,
                approvalReference,
                operationalApprovalReference);
    }
}
