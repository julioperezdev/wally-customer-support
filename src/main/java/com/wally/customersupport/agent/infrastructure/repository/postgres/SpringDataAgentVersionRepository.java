package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataAgentVersionRepository extends JpaRepository<AgentVersionJpaEntity, UUID> {

    boolean existsByAgentIdAndAgentVersion(String agentId, int agentVersion);

    Optional<AgentVersionJpaEntity> findByAgentIdAndAgentVersion(String agentId, int agentVersion);

    List<AgentVersionJpaEntity> findByAgentIdOrderByAgentVersionDesc(String agentId);

    List<AgentVersionJpaEntity> findAllByOrderByAgentIdAscAgentVersionDesc();

    Optional<AgentVersionJpaEntity> findFirstByAgentIdAndStateOrderByAgentVersionDesc(
            String agentId,
            String state);

    @Modifying
    @Transactional
    @Query("""
            update AgentVersionJpaEntity version
               set version.state = :targetState,
                   version.approvedBy = :approvedBy,
                   version.approvedAt = :approvedAt
             where version.agentId = :agentId
               and version.agentVersion = :agentVersion
               and version.state = :expectedState
            """)
    int updateLifecycle(
            @Param("agentId") String agentId,
            @Param("agentVersion") int agentVersion,
            @Param("expectedState") String expectedState,
            @Param("targetState") String targetState,
            @Param("approvedBy") String approvedBy,
            @Param("approvedAt") java.time.Instant approvedAt);
}
