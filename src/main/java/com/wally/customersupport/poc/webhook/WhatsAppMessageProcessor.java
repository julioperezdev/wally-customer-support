package com.wally.customersupport.poc.webhook;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.wally.customersupport.poc.config.WhatsAppPocProperties;
import com.wally.customersupport.poc.whatsapp.WhatsAppClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppMessageProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppMessageProcessor.class);

    private final ObjectMapper objectMapper;
    private final WhatsAppPocProperties properties;
    private final WhatsAppClient whatsAppClient;
    private final Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();

    public WhatsAppMessageProcessor(
            ObjectMapper objectMapper,
            WhatsAppPocProperties properties,
            WhatsAppClient whatsAppClient) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.whatsAppClient = whatsAppClient;
    }

    public void process(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            if (root == null || !"whatsapp_business_account".equals(root.path("object").asText())) {
                LOGGER.info("PoC webhook event ignored: unsupported object");
                return;
            }

            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    JsonNode value = change.path("value");
                    for (JsonNode message : value.path("messages")) {
                        processTextMessage(message);
                    }
                }
            }
        } catch (JacksonException exception) {
            throw new PocPayloadException("Webhook payload is not valid JSON", exception);
        }
    }

    private void processTextMessage(JsonNode message) {
        if (!"text".equals(message.path("type").asText())) {
            return;
        }

        String messageId = message.path("id").asText("");
        String senderWaId = message.path("from").asText("");
        String body = message.path("text").path("body").asText("");

        if (messageId.isBlank() || senderWaId.isBlank() || body.isBlank()) {
            LOGGER.info("PoC text event ignored: required fields missing");
            return;
        }
        if (!processedMessageIds.add(messageId)) {
            LOGGER.info("PoC duplicate inbound event ignored");
            return;
        }
        if (!senderWaId.equals(properties.allowedRecipient())) {
            LOGGER.info("PoC inbound event ignored: sender is not allowlisted");
            return;
        }

        if ("template".equalsIgnoreCase(properties.replyMode())) {
            whatsAppClient.sendTemplate(
                    senderWaId,
                    properties.templateName(),
                    properties.templateLanguageCode(),
                    parseTemplateBodyParameters(properties.templateBodyParameters()));
            LOGGER.info("PoC outbound template sent");
            return;
        }
        if (!"text".equalsIgnoreCase(properties.replyMode())) {
            throw new IllegalStateException("Unsupported PoC reply mode");
        }

        whatsAppClient.sendText(senderWaId, properties.fixedReply());
        LOGGER.info("PoC outbound text sent");
    }

    private static List<String> parseTemplateBodyParameters(String configuredParameters) {
        if (configuredParameters == null || configuredParameters.isBlank()) {
            return List.of();
        }
        return List.of(configuredParameters.split("\\|", -1));
    }
}
