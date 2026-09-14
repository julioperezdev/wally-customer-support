package com.wally.customersupport.agent.application.port.out;

import java.util.List;

import com.wally.customersupport.agent.domain.model.AgentRegistryAuditEvent;

public interface AgentRegistryAuditRepository {

    AgentRegistryAuditEvent save(AgentRegistryAuditEvent event);

    List<AgentRegistryAuditEvent> findRecent(String agentId, int limit);
}
