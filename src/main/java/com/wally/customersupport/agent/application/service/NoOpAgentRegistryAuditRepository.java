package com.wally.customersupport.agent.application.service;

import java.util.List;

import com.wally.customersupport.agent.application.port.out.AgentRegistryAuditRepository;
import com.wally.customersupport.agent.domain.model.AgentRegistryAuditEvent;

/** Compatibility fallback for isolated unit tests that construct services directly. */
final class NoOpAgentRegistryAuditRepository implements AgentRegistryAuditRepository {

    @Override
    public AgentRegistryAuditEvent save(AgentRegistryAuditEvent event) {
        return event;
    }

    @Override
    public List<AgentRegistryAuditEvent> findRecent(String agentId, int limit) {
        return List.of();
    }
}
