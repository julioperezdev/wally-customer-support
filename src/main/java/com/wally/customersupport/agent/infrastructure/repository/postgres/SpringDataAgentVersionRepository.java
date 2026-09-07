package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAgentVersionRepository extends JpaRepository<AgentVersionJpaEntity, UUID> {

    boolean existsByAgentIdAndAgentVersion(String agentId, int agentVersion);

    Optional<AgentVersionJpaEntity> findByAgentIdAndAgentVersion(String agentId, int agentVersion);

    List<AgentVersionJpaEntity> findByAgentIdOrderByAgentVersionDesc(String agentId);

    Optional<AgentVersionJpaEntity> findFirstByAgentIdAndStateOrderByAgentVersionDesc(
            String agentId,
            String state);
}
