package com.wally.customersupport.poc.webhook;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.List;

import tools.jackson.databind.ObjectMapper;
import com.wally.customersupport.poc.config.WhatsAppPocProperties;
import com.wally.customersupport.poc.whatsapp.WhatsAppClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class WhatsAppMessageProcessorTest {

    @Mock
    private WhatsAppClient whatsAppClient;

    private WhatsAppMessageProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new WhatsAppMessageProcessor(
                new ObjectMapper(),
                properties(),
                whatsAppClient);
    }

    @Test
    void sendsOneFixedReplyForAnAllowlistedText() {
        processor.process(textPayload("message-1", "synthetic-recipient", "Hola"));

        verify(whatsAppClient).sendText("synthetic-recipient", "Respuesta de prueba");
    }

    @Test
    void ignoresDuplicateMessageIds() {
        String payload = textPayload("message-1", "synthetic-recipient", "Hola");

        processor.process(payload);
        processor.process(payload);

        verify(whatsAppClient, times(1)).sendText("synthetic-recipient", "Respuesta de prueba");
    }

    @Test
    void ignoresStatusesAndNonAllowlistedSenders() {
        processor.process("""
                {"object":"whatsapp_business_account","entry":[{"changes":[{"value":{"statuses":[{"id":"status-1"}]}}]}]}
                """);
        processor.process(textPayload("message-2", "other-recipient", "Hola"));

        verify(whatsAppClient, never()).sendText(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void sendsHelloWorldTemplateWhenTemplateReplyModeIsConfigured() {
        processor = new WhatsAppMessageProcessor(
                new ObjectMapper(),
                properties("template", "hello_world", "en_US", ""),
                whatsAppClient);

        processor.process(textPayload("message-template-1", "synthetic-recipient", "Hola"));

        verify(whatsAppClient).sendTemplate(
                "synthetic-recipient", "hello_world", "en_US", List.of());
    }

    @Test
    void sendsConfiguredBodyTextParametersInTemplateOrder() {
        processor = new WhatsAppMessageProcessor(
                new ObjectMapper(),
                properties("template", "order_confirmation", "en_US", "John Doe|123456|Aug 30, 2026"),
                whatsAppClient);

        processor.process(textPayload("message-template-2", "synthetic-recipient", "Hola"));

        verify(whatsAppClient).sendTemplate(
                "synthetic-recipient",
                "order_confirmation",
                "en_US",
                List.of("John Doe", "123456", "Aug 30, 2026"));
    }

    private static String textPayload(String id, String sender, String body) {
        return """
                {
                  "object":"whatsapp_business_account",
                  "entry":[{"changes":[{"value":{"messages":[
                    {"id":"%s","from":"%s","type":"text","text":{"body":"%s"}}
                  ]}}]}]
                }
                """.formatted(id, sender, body);
    }

    private static WhatsAppPocProperties properties() {
        return properties("text", "", "", "");
    }

    private static WhatsAppPocProperties properties(
            String replyMode,
            String templateName,
            String templateLanguageCode,
            String templateBodyParameters) {
        return new WhatsAppPocProperties(
                "v23.0",
                "https://graph.facebook.com",
                "synthetic-phone",
                "synthetic-waba",
                "synthetic-access-token",
                "synthetic-verify-token",
                "synthetic-app-secret",
                "synthetic-recipient",
                "Respuesta de prueba",
                replyMode,
                templateName,
                templateLanguageCode,
                templateBodyParameters,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2));
    }
}
