package com.wally.customersupport.agent.application.registry;

import java.util.List;

/** Values and valid assignments used to build registry filters without hardcoding them in a client. */
public record AgentRegistryFilterOptions(
        List<String> agentIds,
        List<String> environments,
        List<String> channels,
        List<String> useCases,
        List<Assignment> assignments) {

    public AgentRegistryFilterOptions {
        agentIds = copy(agentIds);
        environments = copy(environments);
        channels = copy(channels);
        useCases = copy(useCases);
        assignments = copy(assignments);
    }

    public record Assignment(
            String agentId,
            String environment,
            String channel,
            String useCase) {
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
