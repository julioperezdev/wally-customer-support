package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataContactSuppressionRepository
        extends JpaRepository<ContactSuppressionJpaEntity, UUID> {

    boolean existsByActorKeyAndStatus(String actorKey, String status);

    Optional<ContactSuppressionJpaEntity> findByActorKey(String actorKey);

    Optional<ContactSuppressionJpaEntity> findByActorKeyAndStatus(String actorKey, String status);

    Optional<ContactSuppressionJpaEntity> findByActorKeyAndStatusAndReason(
            String actorKey,
            String status,
            String reason);

    @Modifying
    @Query(value = """
            insert into wcs.contact_suppressions (
                id, actor_key, status, reason, source_message_id, created_at, updated_at
            ) values (
                :id, :actorKey, :status, :reason, :sourceMessageId, :createdAt, :updatedAt
            ) on conflict (actor_key) do update set
                status = excluded.status,
                reason = excluded.reason,
                source_message_id = excluded.source_message_id,
                updated_at = excluded.updated_at
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("actorKey") String actorKey,
            @Param("status") String status,
            @Param("reason") String reason,
            @Param("sourceMessageId") UUID sourceMessageId,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt);

    @Modifying
    @Query(value = """
            update wcs.contact_suppressions
            set status = 'REVOKED',
                reason = 'USER_REACTIVATED',
                updated_at = :updatedAt
            where actor_key = :actorKey
              and status = 'DO_NOT_CONTACT'
            """, nativeQuery = true)
    int reactivate(
            @Param("actorKey") String actorKey,
            @Param("updatedAt") Instant updatedAt);
}
