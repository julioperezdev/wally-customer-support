package com.wally.customersupport.agent.application.registry;

import java.util.List;

public record AgentRegistryAgentView(
        String agentId,
        List<AgentRegistryVersionView> versions,
        List<AgentRegistryActivationView> activations) {
}
