package com.wally.customersupport.conversation.infrastructure.http.whatsapp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.InboundMessageCommand;
import com.wally.customersupport.shared.infrastructure.config.WhatsAppProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class WhatsAppInboundPayloadParser {

    private final ObjectMapper objectMapper;
    private final WhatsAppProperties properties;

    public WhatsAppInboundPayloadParser(ObjectMapper objectMapper, WhatsAppProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<InboundMessageCommand> parse(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            if (root == null || !"whatsapp_business_account".equals(root.path("object").asText())) {
                return List.of();
            }

            List<InboundMessageCommand> commands = new ArrayList<>();
            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    JsonNode value = change.path("value");
                    for (JsonNode message : value.path("messages")) {
                        addTextCommand(commands, message);
                    }
                }
            }
            return List.copyOf(commands);
        } catch (JacksonException exception) {
            throw new WhatsAppPayloadException("Webhook payload is not valid JSON", exception);
        }
    }

    private void addTextCommand(List<InboundMessageCommand> commands, JsonNode message) {
        if (!"text".equals(message.path("type").asText())) {
            return;
        }

        String messageId = message.path("id").asText("");
        String senderWaId = message.path("from").asText("");
        String body = message.path("text").path("body").asText("");
        if (isBlank(messageId) || isBlank(senderWaId) || isBlank(body)) {
            return;
        }
        if (!isBlank(properties.allowedRecipient()) && !properties.allowedRecipient().equals(senderWaId)) {
            return;
        }

        commands.add(new InboundMessageCommand(
                Channel.WHATSAPP,
                messageId,
                senderWaId,
                senderWaId,
                body,
                parseTimestamp(message.path("timestamp").asText(""))));
    }

    private static Instant parseTimestamp(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
