package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface SpringDataAgentEvaluationTriggerClaimRepository
        extends Repository<AgentEvaluationTriggerClaimJpaEntity, UUID> {

    @Modifying
    @Query(value = """
            insert into wcs.agent_evaluation_trigger_claims (id, key_hash)
            values (:id, :keyHash)
            on conflict (key_hash) do nothing
            """, nativeQuery = true)
    int insertClaim(@Param("id") UUID id, @Param("keyHash") String keyHash);
}
