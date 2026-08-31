package com.wally.customersupport.domain.model;

import java.util.List;
import java.util.UUID;

public record OutboundMessage(
        UUID conversationId,
        String recipientWaId,
        DeliveryType deliveryType,
        String body,
        String templateName,
        String templateLanguageCode,
        List<String> templateBodyParameters) {

    public OutboundMessage {
        templateBodyParameters = templateBodyParameters == null
                ? List.of()
                : List.copyOf(templateBodyParameters);
    }

    public static OutboundMessage text(UUID conversationId, String recipientWaId, String body) {
        return new OutboundMessage(conversationId, recipientWaId, DeliveryType.TEXT, body, null, null, List.of());
    }

    public static OutboundMessage template(
            UUID conversationId,
            String recipientWaId,
            String templateName,
            String languageCode,
            List<String> bodyParameters) {
        return new OutboundMessage(
                conversationId,
                recipientWaId,
                DeliveryType.TEMPLATE,
                null,
                templateName,
                languageCode,
                bodyParameters);
    }
}
