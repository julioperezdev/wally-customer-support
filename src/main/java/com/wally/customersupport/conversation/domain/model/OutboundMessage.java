package com.wally.customersupport.conversation.domain.model;

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
        List<String> templateBodyParameters,
        String mediaReference) {

    public OutboundMessage {
        templateBodyParameters = templateBodyParameters == null
                ? List.of()
                : List.copyOf(templateBodyParameters);
        if (deliveryType == DeliveryType.IMAGE && isBlank(mediaReference)) {
            throw new IllegalArgumentException("mediaReference is required for image messages");
        }
        if (deliveryType != DeliveryType.IMAGE && mediaReference != null) {
            throw new IllegalArgumentException("mediaReference is only supported for image messages");
        }
    }

    /**
     * Compatibility constructor for text/template messages created before
     * channel-neutral media delivery was introduced.
     */
    public OutboundMessage(
            Channel channel,
            UUID conversationId,
            String recipientId,
            DeliveryType deliveryType,
            String body,
            String templateName,
            String templateLanguageCode,
            List<String> templateBodyParameters) {
        this(channel, conversationId, recipientId, deliveryType, body, templateName,
                templateLanguageCode, templateBodyParameters, null);
    }

    public static OutboundMessage text(Channel channel, UUID conversationId, String recipientId, String body) {
        return new OutboundMessage(channel, conversationId, recipientId, DeliveryType.TEXT, body, null, null, List.of(), null);
    }

    public static OutboundMessage image(
            Channel channel,
            UUID conversationId,
            String recipientId,
            String mediaReference,
            String caption) {
        return new OutboundMessage(
                channel,
                conversationId,
                recipientId,
                DeliveryType.IMAGE,
                caption,
                null,
                null,
                List.of(),
                mediaReference);
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
                bodyParameters,
                null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
