package com.wally.customersupport.conversation.infrastructure.channel.telegram;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.OutboundMessage;
import com.wally.customersupport.shared.infrastructure.config.TelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TelegramOutboundAdapterTest {

    private MockRestServiceServer server;
    private TelegramOutboundAdapter adapter;
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TelegramProperties properties = properties();
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.apiBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new TelegramOutboundAdapter(builder.build(), properties);
    }

    @Test
    void sendsTextToTelegramChat() {
        server.expect(requestTo("https://api.telegram.org/botsynthetic-bot-token/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"chat_id":"synthetic-chat","text":"Hola desde WCS"}
                        """))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        adapter.send(OutboundMessage.text(
                Channel.TELEGRAM, conversationId, "synthetic-chat", "Hola desde WCS"));

        server.verify();
    }

    @Test
    void rejectsTemplatesBecauseTelegramAdapterSupportsTextOnly() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> adapter.send(OutboundMessage.template(
                        Channel.TELEGRAM, conversationId, "synthetic-chat", "template", "en_US", java.util.List.of())));

        assertTrue(exception.getMessage().contains("text messages only"));
    }

    @Test
    void mapsTelegramFailureWithoutExposingBotToken() {
        server.expect(requestTo("https://api.telegram.org/botsynthetic-bot-token/sendMessage"))
                .andRespond(withServerError());

        TelegramException exception = assertThrows(
                TelegramException.class,
                () -> adapter.send(OutboundMessage.text(
                        Channel.TELEGRAM, conversationId, "synthetic-chat", "Hola")));

        assertTrue(exception.getMessage().contains("HTTP 500"));
        assertTrue(!exception.getMessage().contains("synthetic-bot-token"));
    }

    private static TelegramProperties properties() {
        return new TelegramProperties(
                true, "telegram", "https://api.telegram.org", "synthetic-bot-token",
                "synthetic-webhook-secret", "", Duration.ofSeconds(1), Duration.ofSeconds(2));
    }
}
