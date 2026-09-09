package com.wally.customersupport.conversation.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ContactSuppression(
        UUID id,
        String actorKey,
        String status,
        String reason,
        UUID sourceMessageId,
        Instant createdAt,
        Instant updatedAt) {

    public ContactSuppression {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actorKey, "actorKey");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (actorKey.isBlank()) {
            throw new IllegalArgumentException("actorKey must not be blank");
        }
    }

    public static ContactSuppression doNotContact(
            String actorKey,
            UUID sourceMessageId,
            Instant now) {
        return new ContactSuppression(
                UUID.randomUUID(),
                actorKey,
                "DO_NOT_CONTACT",
                "USER_REQUEST",
                sourceMessageId,
                now,
                now);
    }
}
