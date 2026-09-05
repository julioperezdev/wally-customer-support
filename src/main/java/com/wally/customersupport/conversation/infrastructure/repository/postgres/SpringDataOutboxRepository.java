package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.infrastructure.repository.postgres.OutboxJpaEntity;
import com.wally.customersupport.conversation.domain.model.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOutboxRepository extends JpaRepository<OutboxJpaEntity, UUID> {

    List<OutboxJpaEntity> findByStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            List<OutboxStatus> statuses,
            Instant now,
            Pageable pageable);
}
