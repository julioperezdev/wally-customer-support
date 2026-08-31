package com.wally.customersupport.adapter.out.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.domain.model.ProcessingAttempt;
import com.wally.customersupport.domain.model.ProcessingAttemptStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "processing_attempts", schema = "wcs")
public class ProcessingAttemptJpaEntity {

    @Id
    private UUID id;

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProcessingAttemptStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProcessingAttemptJpaEntity() {
    }

    public ProcessingAttemptJpaEntity(ProcessingAttempt attempt) {
        this.id = attempt.id();
        this.messageId = attempt.messageId();
        this.status = attempt.status();
        this.attemptCount = attempt.attemptCount();
        this.lastError = attempt.lastError();
        this.createdAt = attempt.createdAt();
        this.updatedAt = attempt.updatedAt();
    }
}
