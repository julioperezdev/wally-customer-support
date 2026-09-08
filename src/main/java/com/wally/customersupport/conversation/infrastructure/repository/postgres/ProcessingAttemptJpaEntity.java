package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.ProcessingAttempt;
import com.wally.customersupport.conversation.domain.model.ProcessingAttemptStatus;
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

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "started_at")
    private Instant startedAt;

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
        this.availableAt = attempt.availableAt();
        this.startedAt = attempt.startedAt();
    }

    public ProcessingAttempt toDomain() {
        return new ProcessingAttempt(
                id,
                messageId,
                status,
                attemptCount,
                lastError,
                createdAt,
                updatedAt,
                availableAt,
                startedAt);
    }

    public void markCompleted(Instant now) {
        status = ProcessingAttemptStatus.COMPLETED;
        updatedAt = now;
        startedAt = null;
        lastError = null;
    }

    public void markFailed(String error, Instant nextAvailableAt, boolean exhausted, Instant now) {
        status = exhausted ? ProcessingAttemptStatus.FAILED : ProcessingAttemptStatus.PENDING;
        lastError = error;
        availableAt = nextAvailableAt;
        updatedAt = now;
        startedAt = null;
    }
}
