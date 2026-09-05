package com.wally.customersupport.conversation.infrastructure.http.whatsapp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;

import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.InboundMessageCommand;
import com.wally.customersupport.shared.infrastructure.config.WhatsAppProperties;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WhatsAppInboundPayloadParserTest {

    @Test
    void parsesTextMessagesAndIgnoresStatuses() {
        WhatsAppInboundPayloadParser parser = new WhatsAppInboundPayloadParser(
                new ObjectMapper(), properties(""));

        List<InboundMessageCommand> commands = parser.parse("""
                {
                  "object":"whatsapp_business_account",
                  "entry":[{"changes":[{"value":{
                    "statuses":[{"id":"status-1"}],
                    "messages":[{"id":"message-1","from":"synthetic-recipient",
                      "timestamp":"1788091200","type":"text","text":{"body":"Hola"}}]
                  }}]}]
                }
                """);

        assertEquals(List.of(new InboundMessageCommand(
                Channel.WHATSAPP,
                "message-1",
                "synthetic-recipient",
                "synthetic-recipient",
                "Hola",
                Instant.ofEpochSecond(1788091200))), commands);
    }

    @Test
    void appliesOptionalLocalAllowlistWithoutLoggingOrPersistingOtherSenders() {
        WhatsAppInboundPayloadParser parser = new WhatsAppInboundPayloadParser(
                new ObjectMapper(), properties("synthetic-allowed"));

        assertEquals(List.of(), parser.parse("""
                {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{
                  "messages":[{"id":"message-1","from":"synthetic-other","type":"text","text":{"body":"Hola"}}]
                }}]}]}
                """));
    }

    private static WhatsAppProperties properties(String allowedRecipient) {
        return new WhatsAppProperties(
                "mock", "v25.0", "https://graph.facebook.com", "synthetic-phone", "synthetic-waba",
                "synthetic-access-token", "synthetic-verify-token", "synthetic-app-secret", allowedRecipient,
                java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(2));
    }
}
