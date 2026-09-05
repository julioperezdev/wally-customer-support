package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.util.UUID;

import com.wally.customersupport.conversation.infrastructure.repository.postgres.ProcessingAttemptJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProcessingAttemptRepository extends JpaRepository<ProcessingAttemptJpaEntity, UUID> {
}
