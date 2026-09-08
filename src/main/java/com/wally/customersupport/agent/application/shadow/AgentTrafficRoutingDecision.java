package com.wally.customersupport.agent.application.shadow;

/** Decision that prevents shadow output from reaching a customer channel. */
public record AgentTrafficRoutingDecision(
        AgentTrafficMode mode,
        boolean candidateSelected,
        boolean candidateResponsePublished,
        boolean fallbackToActive,
        String reason) {
}
