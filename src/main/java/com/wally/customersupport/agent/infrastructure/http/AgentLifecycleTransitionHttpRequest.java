package com.wally.customersupport.agent.infrastructure.http;

import com.wally.customersupport.agent.application.registry.AgentLifecycleTransitionCommand;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;

/** HTTP input for a protected lifecycle transition. */
public record AgentLifecycleTransitionHttpRequest(
        AgentLifecycleState targetState,
        String reason,
        String approvalReference,
        String operationalApprovalReference) {

    AgentLifecycleTransitionCommand toCommand(String agentId, int version) {
        return new AgentLifecycleTransitionCommand(
                agentId,
                version,
                targetState,
                reason,
                approvalReference,
                operationalApprovalReference);
    }
}
