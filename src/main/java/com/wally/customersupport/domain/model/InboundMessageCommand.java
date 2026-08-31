package com.wally.customersupport.domain.model;

import java.time.Instant;

public record InboundMessageCommand(
        Channel channel,
        String externalMessageId,
        String externalConversationId,
        String customerWaId,
        String body,
        Instant occurredAt) {
}
