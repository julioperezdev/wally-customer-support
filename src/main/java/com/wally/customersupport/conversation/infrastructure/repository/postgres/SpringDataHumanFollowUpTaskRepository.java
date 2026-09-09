package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.HumanFollowUpStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataHumanFollowUpTaskRepository
        extends JpaRepository<HumanFollowUpTaskJpaEntity, UUID> {

    Optional<HumanFollowUpTaskJpaEntity> findBySourceMessageIdAndReason(UUID sourceMessageId, String reason);

    List<HumanFollowUpTaskJpaEntity> findByStatusInOrderByDueAtAsc(Collection<HumanFollowUpStatus> statuses);

    long countByStatusIn(Collection<HumanFollowUpStatus> statuses);

    @Modifying
    @Query(value = """
            update wcs.human_follow_up_tasks
               set status = 'IN_PROGRESS', assigned_to = :actor, updated_at = :updatedAt
             where id = :id and status = 'OPEN'
            """, nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("actor") String actor, @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Query(value = """
            update wcs.human_follow_up_tasks
               set status = 'OPEN', assigned_to = null, updated_at = :updatedAt
             where id = :id and status = 'IN_PROGRESS' and assigned_to = :actor
            """, nativeQuery = true)
    int release(@Param("id") UUID id, @Param("actor") String actor, @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Query(value = """
            update wcs.human_follow_up_tasks
               set status = 'DONE', completed_at = :updatedAt, updated_at = :updatedAt
             where id = :id and status = 'IN_PROGRESS' and assigned_to = :actor
            """, nativeQuery = true)
    int resolve(@Param("id") UUID id, @Param("actor") String actor, @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Query(value = """
            insert into wcs.human_follow_up_tasks (
                id, conversation_id, source_message_id, reason, priority, status,
                due_at, created_at, updated_at, completed_at
            ) values (
                :id, :conversationId, :sourceMessageId, :reason, :priority, :status,
                :dueAt, :createdAt, :updatedAt, :completedAt
            ) on conflict (source_message_id, reason) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("conversationId") UUID conversationId,
            @Param("sourceMessageId") UUID sourceMessageId,
            @Param("reason") String reason,
            @Param("priority") String priority,
            @Param("status") String status,
            @Param("dueAt") Instant dueAt,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("completedAt") Instant completedAt);
}
