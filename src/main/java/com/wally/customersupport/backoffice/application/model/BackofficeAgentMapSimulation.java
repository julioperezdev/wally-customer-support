package com.wally.customersupport.backoffice.application.model;

import java.util.List;

public record BackofficeAgentMapSimulation(
        String environment,
        String channel,
        String useCase,
        String disabledAgentId,
        Integer disabledVersion,
        boolean changed,
        String outcome,
        String reason,
        List<BackofficeRouteStep> route) {

    public BackofficeAgentMapSimulation {
        route = route == null ? List.of() : List.copyOf(route);
    }

    public record BackofficeRouteStep(
            String kind,
            String agentId,
            Integer version,
            String reason) {
    }
}
