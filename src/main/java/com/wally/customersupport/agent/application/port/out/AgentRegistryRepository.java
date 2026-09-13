package com.wally.customersupport.agent.application.port.out;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import com.wally.customersupport.agent.domain.model.AgentActivation;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import com.wally.customersupport.agent.domain.model.AgentVersion;

public interface AgentRegistryRepository {

    AgentVersion saveVersion(AgentVersion version);

    Optional<AgentVersion> findVersion(String agentId, int version);

    List<AgentVersion> findVersions(String agentId);

    List<AgentVersion> findAllVersions();

    Optional<AgentVersion> findLatestVersion(String agentId, AgentLifecycleState state);

    /**
     * Changes lifecycle metadata only. Definition content remains immutable
     * after the original version row is created.
     */
    AgentVersion updateLifecycle(
            String agentId,
            int version,
            AgentLifecycleState expectedState,
            AgentLifecycleState targetState,
            String approvedBy,
            Instant approvedAt);

    AgentActivation saveActivation(AgentActivation activation);

    List<AgentActivation> findAllActivations();

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
