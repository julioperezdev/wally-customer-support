package com.wally.customersupport.poc.webhook;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.wally.customersupport.poc.config.WhatsAppPocProperties;
import com.wally.customersupport.poc.whatsapp.WhatsAppClient;
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
    private WhatsAppMessageProcessor messageProcessor;

    private MockMvc mockMvc;
    private WhatsAppPocProperties properties;

    @BeforeEach
    void setUp() {
        properties = new WhatsAppPocProperties(
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
        mockMvc = MockMvcBuilders.standaloneSetup(
                new WhatsAppWebhookController(properties, new HmacVerifier(), messageProcessor)).build();
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
    void acceptsSignedBodyAndDelegatesAfterVerification() throws Exception {
        String body = "{\"object\":\"whatsapp_business_account\"}";

        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", signature(body, "synthetic-app-secret"))
                        .content(body))
                .andExpect(status().isOk());

        verify(messageProcessor).process(body);
    }

    @Test
    void rejectsUnsignedBodyBeforeDelegating() throws Exception {
        String body = "{\"object\":\"whatsapp_business_account\"}";

        mockMvc.perform(post("/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    private static String signature(String body, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
