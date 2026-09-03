package com.wally.customersupport.adapter.out.whatsapp.meta;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.domain.model.OutboundMessage;
import com.wally.customersupport.domain.model.Channel;
import com.wally.customersupport.infrastructure.config.WhatsAppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MetaWhatsAppOutboundAdapterTest {

    private MockRestServiceServer server;
    private MetaWhatsAppOutboundAdapter adapter;
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        WhatsAppProperties properties = properties();
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.graphApiBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new MetaWhatsAppOutboundAdapter(builder.build(), properties);
    }

    @Test
    void sendsTextPayloadWithBearerAuthentication() {
        server.expect(requestTo("https://graph.facebook.com/v25.0/synthetic-phone/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-access-token"))
                .andExpect(content().json("""
                        {
                          "messaging_product":"whatsapp",
                          "to":"synthetic-recipient",
                          "type":"text",
                          "text":{"body":"Hola"}
                        }
                        """))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"synthetic-message-id\"}]}", MediaType.APPLICATION_JSON));

        adapter.send(OutboundMessage.text(Channel.WHATSAPP, conversationId, "synthetic-recipient", "Hola"));

        server.verify();
    }

    @Test
    void sendsTemplateWithBodyParametersInOrder() {
        server.expect(requestTo("https://graph.facebook.com/v25.0/synthetic-phone/messages"))
                .andExpect(content().json("""
                        {
                          "messaging_product":"whatsapp",
                          "to":"synthetic-recipient",
                          "type":"template",
                          "template":{
                            "name":"order_confirmation",
                            "language":{"code":"en_US"},
                            "components":[{"type":"body","parameters":[
                              {"type":"text","text":"John Doe"},
                              {"type":"text","text":"123456"},
                              {"type":"text","text":"Aug 30, 2026"}
                            ]}]
                          }
                        }
                        """))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        adapter.send(OutboundMessage.template(
                Channel.WHATSAPP,
                conversationId,
                "synthetic-recipient",
                "order_confirmation",
                "en_US",
                List.of("John Doe", "123456", "Aug 30, 2026")));

        server.verify();
    }

    @Test
    void mapsMetaFailureWithoutExposingCredentials() {
        server.expect(requestTo("https://graph.facebook.com/v25.0/synthetic-phone/messages"))
                .andRespond(withServerError());

        MetaWhatsAppException exception = assertThrows(
                MetaWhatsAppException.class,
                () -> adapter.send(OutboundMessage.text(
                        Channel.WHATSAPP, conversationId, "synthetic-recipient", "Hola")));

        assertTrue(exception.getMessage().contains("HTTP 500"));
        assertTrue(!exception.getMessage().contains("synthetic-access-token"));
    }

    private static WhatsAppProperties properties() {
        return new WhatsAppProperties(
                "meta", "v25.0", "https://graph.facebook.com", "synthetic-phone", "synthetic-waba",
                "synthetic-access-token", "synthetic-verify-token", "synthetic-app-secret", "",
                Duration.ofSeconds(1), Duration.ofSeconds(2));
    }
}
