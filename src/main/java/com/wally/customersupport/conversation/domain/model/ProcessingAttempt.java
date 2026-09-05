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
        Instant updatedAt) {
}
