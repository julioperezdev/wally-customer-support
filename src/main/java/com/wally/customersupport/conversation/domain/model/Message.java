package com.wally.customersupport.conversation.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Message(
        UUID id,
        UUID conversationId,
        Channel channel,
        String externalMessageId,
        MessageDirection direction,
        MessageType messageType,
        String body,
        Instant occurredAt,
        Instant createdAt) {
}
