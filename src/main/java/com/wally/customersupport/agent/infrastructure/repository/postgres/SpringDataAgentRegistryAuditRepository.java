package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAgentRegistryAuditRepository extends JpaRepository<AgentRegistryAuditJpaEntity, UUID> {

    List<AgentRegistryAuditJpaEntity> findTop200ByOrderByOccurredAtDesc();
}
