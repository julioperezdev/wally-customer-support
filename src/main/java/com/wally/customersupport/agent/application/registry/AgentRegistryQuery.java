package com.wally.customersupport.agent.application.registry;

import java.util.Objects;

/** Bounded, provider-neutral filters for the internal registry read model. */
public record AgentRegistryQuery(
        String agentId,
        String environment,
        String channel,
        String useCase,
        int limit) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    public AgentRegistryQuery {
        agentId = normalize(agentId);
        environment = normalize(environment);
        channel = normalize(channel);
        useCase = normalize(useCase);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }

    public static AgentRegistryQuery of(String agentId, String environment, String channel, String useCase) {
        return new AgentRegistryQuery(agentId, environment, channel, useCase, DEFAULT_LIMIT);
    }

    public boolean matchesAgent(String candidateAgentId) {
        return agentId == null || Objects.equals(agentId, candidateAgentId);
    }

    public boolean matchesActivation(
            String candidateEnvironment,
            String candidateChannel,
            String candidateUseCase) {
        return (environment == null || Objects.equals(environment, candidateEnvironment))
                && (channel == null || Objects.equals(channel, candidateChannel))
                && (useCase == null || Objects.equals(useCase, candidateUseCase));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
