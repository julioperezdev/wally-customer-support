package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataAgentEvaluationRunRepository
        extends JpaRepository<AgentEvaluationRunJpaEntity, UUID> {

    @Query("""
            select run
            from AgentEvaluationRunJpaEntity run
            where run.datasetVersion = coalesce(:datasetVersion, run.datasetVersion)
              and run.agentId = coalesce(:agentId, run.agentId)
              and run.agentVersion = coalesce(:agentVersion, run.agentVersion)
              and run.provider = coalesce(:provider, run.provider)
              and run.modelId = coalesce(:modelId, run.modelId)
              and run.completedAt >= coalesce(:completedFrom, run.completedAt)
              and run.completedAt <= coalesce(:completedTo, run.completedAt)
            """)
    Page<AgentEvaluationRunJpaEntity> search(
            @Param("datasetVersion") String datasetVersion,
            @Param("agentId") String agentId,
            @Param("agentVersion") String agentVersion,
            @Param("provider") String provider,
            @Param("modelId") String modelId,
            @Param("completedFrom") Instant completedFrom,
            @Param("completedTo") Instant completedTo,
            Pageable pageable);
}
