package com.wally.customersupport.conversation.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record HumanFollowUpTask(
        UUID id,
        UUID conversationId,
        UUID sourceMessageId,
        String reason,
        HumanFollowUpPriority priority,
        HumanFollowUpStatus status,
        Instant dueAt,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {

    public HumanFollowUpTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(dueAt, "dueAt");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    public static HumanFollowUpTask open(
            UUID conversationId,
            UUID sourceMessageId,
            String reason,
            HumanFollowUpPriority priority,
            Instant dueAt,
            Instant now) {
        return new HumanFollowUpTask(
                UUID.randomUUID(),
                conversationId,
                sourceMessageId,
                reason,
                priority,
                HumanFollowUpStatus.OPEN,
                dueAt,
                now,
                now,
                null);
    }
}
