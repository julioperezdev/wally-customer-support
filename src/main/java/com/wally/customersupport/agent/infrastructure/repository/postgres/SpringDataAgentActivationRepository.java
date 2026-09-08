package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAgentActivationRepository extends JpaRepository<AgentActivationJpaEntity, UUID> {

    List<AgentActivationJpaEntity> findAllByOrderByAgentIdAscActivatedAtDesc();

    Optional<AgentActivationJpaEntity> findFirstByAgentIdAndEnvironmentAndChannelAndUseCaseOrderByActivatedAtDesc(
            String agentId,
            String environment,
            String channel,
            String useCase);
}
