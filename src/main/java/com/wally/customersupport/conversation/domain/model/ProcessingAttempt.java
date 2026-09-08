package com.wally.customersupport.conversation.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ProcessingAttempt(
        UUID id,
        UUID messageId,
        ProcessingAttemptStatus status,
        int attemptCount,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        Instant availableAt,
        Instant startedAt) {

    public static ProcessingAttempt pending(UUID messageId, Instant now) {
        return new ProcessingAttempt(
                UUID.randomUUID(),
                messageId,
                ProcessingAttemptStatus.PENDING,
                0,
                null,
                now,
                now,
                now,
                null);
    }
}
