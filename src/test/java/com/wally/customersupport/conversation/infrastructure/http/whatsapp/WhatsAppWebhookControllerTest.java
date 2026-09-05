package com.wally.customersupport.conversation.infrastructure.http.whatsapp;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.wally.customersupport.conversation.application.port.in.InboundMessagePort;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.InboundMessageCommand;
import com.wally.customersupport.conversation.domain.model.InboundMessageResult;
import com.wally.customersupport.shared.infrastructure.config.WhatsAppProperties;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookControllerTest {

    @Mock
    private InboundMessagePort inboundMessagePort;

    @Mock
    private WhatsAppInboundPayloadParser payloadParser;

    private MockMvc mockMvc;
    private WhatsAppProperties properties;

    @BeforeEach
    void setUp() {
        properties = new WhatsAppProperties(
                "mock", "v25.0", "https://graph.facebook.com", "synthetic-phone", "synthetic-waba",
                "synthetic-access-token", "synthetic-verify-token", "synthetic-app-secret", "",
                java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(2));
        mockMvc = MockMvcBuilders.standaloneSetup(new WhatsAppWebhookController(
                properties, new HmacVerifier(), payloadParser, inboundMessagePort)).build();
    }

    @Test
    void returnsExactChallengeForValidVerification() throws Exception {
        mockMvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "synthetic-verify-token")
                        .param("hub.challenge", "challenge-123"))
                .andExpect(status().isOk())
                .andExpect(content().string(is("challenge-123")));
    }

    @Test
    void rejectsInvalidVerificationToken() throws Exception {
        mockMvc.perform(get("/webhook/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong")
                        .param("hub.challenge", "challenge-123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void verifiesSignedBodyBeforeDelegatingParsedCommands() throws Exception {
        String body = "{\"object\":\"whatsapp_business_account\"}";
        InboundMessageCommand command = new InboundMessageCommand(
                Channel.WHATSAPP, "message-1", "conversation-1", "customer-1", "Hola", null);
        org.mockito.Mockito.when(payloadParser.parse(body)).thenReturn(java.util.List.of(command));
        org.mockito.Mockito.when(inboundMessagePort.accept(command)).thenReturn(InboundMessageResult.accepted());

        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", signature(body, "synthetic-app-secret"))
                        .content(body))
                .andExpect(status().isOk());

        verify(inboundMessagePort).accept(command);
    }

    @Test
    void rejectsUnsignedBodyBeforeParsing() throws Exception {
        String body = "{\"object\":\"whatsapp_business_account\"}";

        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    private static String signature(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
