package com.wally.customersupport.adapter.in.web.telegram;

import java.time.Instant;
import java.util.List;

import com.wally.customersupport.domain.model.Channel;
import com.wally.customersupport.domain.model.InboundMessageCommand;
import com.wally.customersupport.infrastructure.config.TelegramProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class TelegramInboundPayloadParser {

    private final ObjectMapper objectMapper;
    private final TelegramProperties properties;

    public TelegramInboundPayloadParser(ObjectMapper objectMapper, TelegramProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<InboundMessageCommand> parse(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            if (root == null || !root.isObject()) {
                return List.of();
            }

            long updateId = root.path("update_id").asLong(-1);
            JsonNode message = root.path("message");
            if (updateId < 0 || !message.isObject()) {
                return List.of();
            }

            String chatId = message.path("chat").path("id").asText("");
            String senderId = message.path("from").path("id").asText(chatId);
            String body = message.path("text").asText("");
            if (isBlank(chatId) || isBlank(senderId) || isBlank(body)) {
                return List.of();
            }
            if (!isBlank(properties.allowedChatId()) && !properties.allowedChatId().equals(chatId)) {
                return List.of();
            }

            return List.of(new InboundMessageCommand(
                    Channel.TELEGRAM,
                    Long.toString(updateId),
                    chatId,
                    senderId,
                    body,
                    parseTimestamp(message.path("date").asLong(0))));
        } catch (JacksonException exception) {
            throw new TelegramPayloadException("Telegram webhook payload is not valid JSON", exception);
        }
    }

    private static Instant parseTimestamp(long epochSeconds) {
        return epochSeconds <= 0 ? null : Instant.ofEpochSecond(epochSeconds);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
