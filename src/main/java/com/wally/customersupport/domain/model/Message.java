package com.wally.customersupport.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Message(
        UUID id,
        UUID conversationId,
        String externalMessageId,
        MessageDirection direction,
        MessageType messageType,
        String body,
        Instant occurredAt,
        Instant createdAt) {
}
