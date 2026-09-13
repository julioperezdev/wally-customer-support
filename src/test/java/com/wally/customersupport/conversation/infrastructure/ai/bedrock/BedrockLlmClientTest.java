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
import java.time.Duration;
import java.util.List;
import java.util.Set;

import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.ClasspathPromptRegistry;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptDefinition;
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

    @Test
    void appliesThePublishedAgentModelInferenceAndPromptContract() {
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        when(converseClient.completeForAgent(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(), anyFloat(),
                anyString(), anyString(), org.mockito.ArgumentMatchers.any(AgentRuntimeDefinition.class)))
                .thenReturn("respuesta de agente versionado");
        ClasspathPromptRegistry promptRegistry = new ClasspathPromptRegistry();
        PromptDefinition prompt = promptRegistry.responsePrompt("conversation-response-v1");
        AgentRuntimeDefinition definition = new AgentRuntimeDefinition(
                "knowledge-specialist",
                3,
                "Knowledge specialist",
                "Responder con conocimiento documental aprobado",
                "bedrock",
                "openai.gpt-oss-20b-1:0",
                new AgentInferenceParameters(new BigDecimal("0.35"), new BigDecimal("0.72")),
                prompt.version(),
                prompt.sha256(),
                "knowledge-input-v1",
                "knowledge-output-v1",
                Set.of("knowledge.retrieve"),
                Set.of("wcs-knowledge-base"),
                "conversation-summary-v1",
                "grounded-customer-support-v1",
                Duration.ofSeconds(8),
                2,
                1_000,
                700,
                new BigDecimal("0.01"),
                "safe-fallback",
                "knowledge-response-v1");
        BedrockLlmClient client = new BedrockLlmClient(
                converseClient,
                new AiResponseProperties(
                        "conversation-response-v1", 512, new BigDecimal("0.1"), 2_000, 2, 120, 100),
                promptRegistry);

        String reply = client.generateReply(new ConversationContext(
                null,
                "customer-1",
                "¿Dónde están ubicados?",
                List.of("¿Dónde están ubicados?"),
                List.of()), definition);

        assertEquals("respuesta de agente versionado", reply);
        verify(converseClient).completeForAgent(
                eq("response-generation"),
                eq("conversation.reply.generate"),
                anyString(),
                anyString(),
                eq(700),
                eq(0.35f),
                eq(0.72f),
                eq("conversation-response-v1"),
                eq(prompt.sha256()),
                eq(definition));
    }

    @Test
    void failsClosedWhenThePublishedPromptHashDoesNotMatch() {
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        ClasspathPromptRegistry promptRegistry = new ClasspathPromptRegistry();
        AgentRuntimeDefinition definition = new AgentRuntimeDefinition(
                "knowledge-specialist", 3, "Knowledge specialist", "Documentary support", "bedrock", "model-v1",
                AgentInferenceParameters.deterministic(), "conversation-response-v1",
                "0000000000000000000000000000000000000000000000000000000000000000",
                "input-v1", "output-v1", Set.of(), Set.of(), "memory-v1", "response-v1",
                Duration.ofSeconds(8), 1, 1_000, 700, new BigDecimal("0.01"), null, "eval-v1");
        BedrockLlmClient client = new BedrockLlmClient(
                converseClient,
                new AiResponseProperties(
                        "conversation-response-v1", 512, new BigDecimal("0.1"), 2_000, 2, 120, 100),
                promptRegistry);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> client.generateReply(new ConversationContext(
                        null, "customer-1", "consulta", List.of("consulta"), List.of()), definition));
        org.mockito.Mockito.verifyNoInteractions(converseClient);
    }
}
