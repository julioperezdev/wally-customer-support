package com.wally.customersupport.domain.model;

import java.util.List;
import java.util.UUID;

public record OutboundMessage(
        Channel channel,
        UUID conversationId,
        String recipientId,
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

    public static OutboundMessage text(Channel channel, UUID conversationId, String recipientId, String body) {
        return new OutboundMessage(channel, conversationId, recipientId, DeliveryType.TEXT, body, null, null, List.of());
    }

    public static OutboundMessage template(
            Channel channel,
            UUID conversationId,
            String recipientId,
            String templateName,
            String languageCode,
            List<String> bodyParameters) {
        return new OutboundMessage(
                channel,
                conversationId,
                recipientId,
                DeliveryType.TEMPLATE,
                null,
                templateName,
                languageCode,
                bodyParameters);
    }
}
