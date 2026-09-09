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

    Optional<ContactSuppressionJpaEntity> findByActorKeyAndStatus(String actorKey, String status);

    @Modifying
    @Query(value = """
            insert into wcs.contact_suppressions (
                id, actor_key, status, reason, source_message_id, created_at, updated_at
            ) values (
                :id, :actorKey, :status, :reason, :sourceMessageId, :createdAt, :updatedAt
            ) on conflict (actor_key) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("actorKey") String actorKey,
            @Param("status") String status,
            @Param("reason") String reason,
            @Param("sourceMessageId") UUID sourceMessageId,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt);
}
