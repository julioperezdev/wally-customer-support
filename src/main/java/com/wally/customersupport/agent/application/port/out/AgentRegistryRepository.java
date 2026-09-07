package com.wally.customersupport.agent.application.port.out;

import java.util.List;
import java.util.Optional;

import com.wally.customersupport.agent.domain.model.AgentActivation;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import com.wally.customersupport.agent.domain.model.AgentVersion;

public interface AgentRegistryRepository {

    AgentVersion saveVersion(AgentVersion version);

    Optional<AgentVersion> findVersion(String agentId, int version);

    List<AgentVersion> findVersions(String agentId);

    Optional<AgentVersion> findLatestVersion(String agentId, AgentLifecycleState state);

    AgentActivation saveActivation(AgentActivation activation);

    Optional<AgentActivation> findLatestActivation(
            String agentId,
            String environment,
            String channel,
            String useCase);

    Optional<AgentActivation> findActiveActivation(
            String agentId,
            String environment,
            String channel,
            String useCase);
}
