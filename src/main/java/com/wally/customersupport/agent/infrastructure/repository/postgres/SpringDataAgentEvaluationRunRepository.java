package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAgentEvaluationRunRepository
        extends JpaRepository<AgentEvaluationRunJpaEntity, UUID> {
}
