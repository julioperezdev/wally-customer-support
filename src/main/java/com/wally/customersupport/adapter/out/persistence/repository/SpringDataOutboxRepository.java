package com.wally.customersupport.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.adapter.out.persistence.entity.OutboxJpaEntity;
import com.wally.customersupport.domain.model.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOutboxRepository extends JpaRepository<OutboxJpaEntity, UUID> {

    List<OutboxJpaEntity> findByStatusInAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            List<OutboxStatus> statuses,
            Instant now,
            Pageable pageable);
}
