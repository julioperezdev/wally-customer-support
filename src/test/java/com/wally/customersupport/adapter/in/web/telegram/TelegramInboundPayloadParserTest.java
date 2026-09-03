package com.wally.customersupport.adapter.in.web.telegram;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.wally.customersupport.domain.model.Channel;
import com.wally.customersupport.domain.model.InboundMessageCommand;
import com.wally.customersupport.infrastructure.config.TelegramProperties;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TelegramInboundPayloadParserTest {

    @Test
    void parsesTextMessageIntoTheChannelAgnosticCommand() {
        TelegramInboundPayloadParser parser = new TelegramInboundPayloadParser(
                new ObjectMapper(), properties(""));

        List<InboundMessageCommand> commands = parser.parse("""
                {
                  "update_id": 123456789,
                  "message": {
                    "message_id": 17,
                    "from": {"id": 9001, "is_bot": false},
                    "chat": {"id": 9001, "type": "private"},
                    "date": 1788091200,
                    "text": "Necesito un buzo"
                  }
                }
                """);

        assertEquals(List.of(new InboundMessageCommand(
                Channel.TELEGRAM,
                "123456789",
                "9001",
                "9001",
                "Necesito un buzo",
                Instant.ofEpochSecond(1788091200))), commands);
    }

    @Test
    void appliesOptionalChatAllowlist() {
        TelegramInboundPayloadParser parser = new TelegramInboundPayloadParser(
                new ObjectMapper(), properties("9002"));

        assertEquals(List.of(), parser.parse("""
                {"update_id":123,"message":{"from":{"id":9001},"chat":{"id":9001},"text":"Hola"}}
                """));
    }

    @Test
    void ignoresNonTextUpdates() {
        TelegramInboundPayloadParser parser = new TelegramInboundPayloadParser(
                new ObjectMapper(), properties(""));

        assertEquals(List.of(), parser.parse("""
                {"update_id":123,"message":{"from":{"id":9001},"chat":{"id":9001},"photo":[]}}
                """));
    }

    private static TelegramProperties properties(String allowedChatId) {
        return new TelegramProperties(
                true, "telegram", "https://api.telegram.org", "synthetic-bot-token",
                "synthetic-webhook-secret", allowedChatId,
                Duration.ofSeconds(1), Duration.ofSeconds(2));
    }
}
