package com.wally.customersupport.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OutboxMessage(
        UUID id,
        UUID aggregateId,
        String eventType,
        OutboundMessage message,
        OutboxStatus status,
        int attempts,
        Instant availableAt,
        Instant createdAt,
        Instant sentAt,
        String lastError) {

    public static OutboxMessage pendingReply(OutboundMessage message, Instant now) {
        return new OutboxMessage(
                UUID.randomUUID(),
                message.conversationId(),
                "CUSTOMER_REPLY_REQUESTED",
                message,
                OutboxStatus.PENDING,
                0,
                now,
                now,
                null,
                null);
    }
}
