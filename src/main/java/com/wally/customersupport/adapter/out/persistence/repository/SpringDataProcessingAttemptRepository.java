package com.wally.customersupport.adapter.out.persistence.repository;

import java.util.UUID;

import com.wally.customersupport.adapter.out.persistence.entity.ProcessingAttemptJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProcessingAttemptRepository extends JpaRepository<ProcessingAttemptJpaEntity, UUID> {
}
