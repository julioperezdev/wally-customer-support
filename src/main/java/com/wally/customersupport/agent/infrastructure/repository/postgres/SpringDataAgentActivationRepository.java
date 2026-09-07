package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAgentActivationRepository extends JpaRepository<AgentActivationJpaEntity, UUID> {

    Optional<AgentActivationJpaEntity> findFirstByAgentIdAndEnvironmentAndChannelAndUseCaseOrderByActivatedAtDesc(
            String agentId,
            String environment,
            String channel,
            String useCase);
}
