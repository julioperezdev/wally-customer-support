package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.infrastructure.repository.postgres.OutboxJpaEntity;
import com.wally.customersupport.conversation.domain.model.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataOutboxRepository extends JpaRepository<OutboxJpaEntity, UUID> {

    List<OutboxJpaEntity> findByStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            List<OutboxStatus> statuses,
            Instant now,
            Pageable pageable);

    @Modifying
    @Query(value = """
            update wcs.outbox_messages
               set status = 'PENDING'
             where status = 'PROCESSING'
               and available_at <= :cutoff
            """, nativeQuery = true)
    int requeueStaleProcessing(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query(value = """
            update wcs.outbox_messages
               set status = 'PROCESSING',
                   attempts = attempts + 1,
                   available_at = :now
             where id = :id
               and status = 'PENDING'
               and available_at <= :now
            """, nativeQuery = true)
    int claim(@Param("id") UUID id, @Param("now") Instant now);
}
