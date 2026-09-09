package com.wally.customersupport.backoffice.application.model;

import java.util.List;

public record BackofficeUseCaseMap(
        String environment,
        String channel,
        String useCase,
        List<BackofficeAgentNode> agents,
        List<BackofficeAgentEdge> edges) {

    public BackofficeUseCaseMap {
        agents = agents == null ? List.of() : List.copyOf(agents);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }
}
