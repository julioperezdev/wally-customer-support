package com.wally.customersupport.poc.whatsapp;

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

import com.wally.customersupport.poc.config.WhatsAppPocProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MetaWhatsAppClientTest {

    private MockRestServiceServer server;
    private MetaWhatsAppClient client;

    @BeforeEach
    void setUp() {
        WhatsAppPocProperties properties = properties();
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.graphApiBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MetaWhatsAppClient(builder.build(), properties);
    }

    @Test
    void sendsTextPayloadWithBearerAuthentication() {
        server.expect(requestTo("https://graph.facebook.com/v23.0/synthetic-phone/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-access-token"))
                .andExpect(content().json("""
                        {
                          "messaging_product":"whatsapp",
                          "to":"synthetic-recipient",
                          "type":"text",
                          "text":{"body":"Hola PoC"}
                        }
                        """))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"synthetic-message-id\"}]}", MediaType.APPLICATION_JSON));

        client.sendText("synthetic-recipient", "Hola PoC");

        server.verify();
    }

    @Test
    void sendsHelloWorldTemplateWithoutComponents() {
        server.expect(requestTo("https://graph.facebook.com/v23.0/synthetic-phone/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer synthetic-access-token"))
                .andExpect(content().json("""
                        {
                          "messaging_product":"whatsapp",
                          "to":"synthetic-recipient",
                          "type":"template",
                          "template":{
                            "name":"hello_world",
                            "language":{"code":"en_US"}
                          }
                        }
                        """))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"synthetic-template-id\"}]}", MediaType.APPLICATION_JSON));

        client.sendTemplate("synthetic-recipient", "hello_world", "en_US", List.of());

        server.verify();
    }

    @Test
    void sendsTemplateWithBodyTextParametersInOrder() {
        server.expect(requestTo("https://graph.facebook.com/v23.0/synthetic-phone/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "messaging_product":"whatsapp",
                          "to":"synthetic-recipient",
                          "type":"template",
                          "template":{
                            "name":"order_confirmation",
                            "language":{"code":"en_US"},
                            "components":[{
                              "type":"body",
                              "parameters":[
                                {"type":"text","text":"John Doe"},
                                {"type":"text","text":"123456"},
                                {"type":"text","text":"Aug 30, 2026"}
                              ]
                            }]
                          }
                        }
                        """))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"synthetic-template-id-2\"}]}", MediaType.APPLICATION_JSON));

        client.sendTemplate(
                "synthetic-recipient",
                "order_confirmation",
                "en_US",
                List.of("John Doe", "123456", "Aug 30, 2026"));

        server.verify();
    }

    @Test
    void mapsMetaFailureWithoutExposingCredentials() {
        server.expect(requestTo("https://graph.facebook.com/v23.0/synthetic-phone/messages"))
                .andRespond(withServerError());

        MetaWhatsAppClientException exception = assertThrows(
                MetaWhatsAppClientException.class,
                () -> client.sendText("synthetic-recipient", "Hola PoC"));

        assertTrue(exception.getMessage().contains("HTTP 500"));
        assertTrue(!exception.getMessage().contains("synthetic-access-token"));
    }

    private static WhatsAppPocProperties properties() {
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
                "text",
                "",
                "",
                "",
                Duration.ofSeconds(1),
                Duration.ofSeconds(2));
    }
}
