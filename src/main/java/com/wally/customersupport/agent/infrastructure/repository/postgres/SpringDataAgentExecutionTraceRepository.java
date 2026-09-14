package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAgentExecutionTraceRepository extends JpaRepository<AgentExecutionTraceJpaEntity, UUID> {

    List<AgentExecutionTraceJpaEntity> findTop500ByOrderByExecutedAtDesc();
}
