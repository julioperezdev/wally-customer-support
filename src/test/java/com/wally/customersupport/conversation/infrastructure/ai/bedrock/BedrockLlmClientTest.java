package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.ClasspathPromptRegistry;
import com.wally.customersupport.shared.infrastructure.config.AiResponseProperties;
import com.wally.customersupport.knowledge.domain.model.KnowledgeChunk;
import org.junit.jupiter.api.Test;

class BedrockLlmClientTest {

    @Test
    void sendsApprovedVersionedPromptAndBoundedContextToBedrock() {
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        when(converseClient.complete(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(),
                anyString(), anyString()))
                .thenReturn("respuesta grounded");
        AiResponseProperties properties = new AiResponseProperties(
                "conversation-response-v1", 512, new BigDecimal("0.1"), 30, 2, 120, 100);
        BedrockLlmClient client = new BedrockLlmClient(
                converseClient, properties, new ClasspathPromptRegistry());

        String reply = client.generateReply(new ConversationContext(
                null,
                "customer-1",
                "¿Dónde están?",
                List.of("mensaje antiguo", "mensaje reciente", "último contexto"),
                List.of(new KnowledgeChunk("Calle Código 123", 0.95, "store-location-v1")),
                "resumen breve"));

        assertEquals("respuesta grounded", reply);
        var prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        var userPrompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(converseClient).complete(
                eq("response-generation"),
                eq("conversation.reply.generate"),
                prompt.capture(),
                userPrompt.capture(),
                eq(512),
                eq(0.1f),
                eq("conversation-response-v1"),
                org.mockito.ArgumentMatchers.anyString());
        assertTrue(prompt.getValue().contains("Ropa de Programador"));
        assertTrue(userPrompt.getValue().contains("último contexto"));
        assertTrue(userPrompt.getValue().contains("Calle Código 123"));
        assertTrue(userPrompt.getValue().length() < 700);
    }
}
