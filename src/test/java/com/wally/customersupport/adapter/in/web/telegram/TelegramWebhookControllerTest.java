package com.wally.customersupport.adapter.in.web.telegram;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;

import com.wally.customersupport.application.port.in.InboundMessagePort;
import com.wally.customersupport.domain.model.Channel;
import com.wally.customersupport.domain.model.InboundMessageCommand;
import com.wally.customersupport.infrastructure.config.TelegramProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TelegramWebhookControllerTest {

    @Mock
    private InboundMessagePort inboundMessagePort;

    @Mock
    private TelegramInboundPayloadParser payloadParser;

    private MockMvc mockMvc;
    private InboundMessageCommand command;

    @BeforeEach
    void setUp() {
        TelegramProperties properties = new TelegramProperties(
                true, "telegram", "https://api.telegram.org", "synthetic-bot-token",
                "synthetic-webhook-secret", "", Duration.ofSeconds(1), Duration.ofSeconds(2));
        command = new InboundMessageCommand(
                Channel.TELEGRAM, "update-1", "chat-1", "customer-1", "Hola", null);
        mockMvc = MockMvcBuilders.standaloneSetup(new TelegramWebhookController(
                properties, payloadParser, inboundMessagePort)).build();
    }

    @Test
    void rejectsRequestWithoutTelegramSecret() throws Exception {
        mockMvc.perform(post("/webhook/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"update_id\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesSecretBeforeDelegatingToApplication() throws Exception {
        when(payloadParser.parse("{\"update_id\":1}")).thenReturn(List.of(command));

        mockMvc.perform(post("/webhook/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Bot-Api-Secret-Token", "synthetic-webhook-secret")
                        .content("{\"update_id\":1}"))
                .andExpect(status().isOk());

        verify(payloadParser).parse("{\"update_id\":1}");
        verify(inboundMessagePort).accept(command);
    }
}
