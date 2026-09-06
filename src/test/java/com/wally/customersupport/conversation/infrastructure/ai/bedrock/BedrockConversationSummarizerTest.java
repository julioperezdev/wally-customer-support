package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BedrockConversationSummarizerTest {

    @Test
    void redactsContactDataBeforeSendingOlderMessagesToBedrock() {
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        when(converseClient.complete(anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat()))
                .thenReturn("Resumen seguro");
        BedrockConversationSummarizer summarizer = new BedrockConversationSummarizer(converseClient);

        summarizer.summarize(
                "El cliente ya consultó.",
                List.of("Escribime a cliente@example.com o al +54 11 5555 1234"));

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(converseClient).complete(
                anyString(), anyString(), anyString(), prompt.capture(), anyInt(), anyFloat());
        assertFalse(prompt.getValue().contains("cliente@example.com"));
        assertFalse(prompt.getValue().contains("+54 11 5555 1234"));
        assertTrue(prompt.getValue().contains("[redacted-email]"));
        assertTrue(prompt.getValue().contains("[redacted-number]"));
    }
}
