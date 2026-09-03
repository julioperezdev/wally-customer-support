package com.wally.customersupport.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Conversation(
        UUID id,
        Channel channel,
        String externalConversationId,
        String externalCustomerId,
        ConversationStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
