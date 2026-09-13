package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.shared.infrastructure.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

@ExtendWith(OutputCaptureExtension.class)
class BedrockConverseClientTest {

    @Test
    void recordsModelTokensCostAndLatencyAsStructuredJson(CapturedOutput output) {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.fromText("respuesta"))
                        .build()))
                .usage(TokenUsage.builder().inputTokens(100).outputTokens(50).totalTokens(150).build())
                .metrics(ConverseMetrics.builder().latencyMs(42L).build())
                .stopReason(StopReason.END_TURN)
                .build());

        AiProperties properties = new AiProperties(
                "bedrock",
                "openai.gpt-oss-20b-1:0",
                "us-east-1",
                "pricing-test-v1",
                new BigDecimal("0.0721"),
                new BigDecimal("0.3090"));

        String result = new BedrockConverseClient(client, properties).complete(
                "response-generation",
                "conversation.reply.generate",
                "system",
                "user",
                512,
                0.2f);

        assertEquals("respuesta", result);
        assertTrue(output.getOut().contains("\"eventType\":\"AI_USAGE_RECORDED\""));
        assertTrue(output.getOut().contains("\"model\":\"openai.gpt-oss-20b-1:0\""));
        assertTrue(output.getOut().contains("\"inputTokens\":100"));
        assertTrue(output.getOut().contains("\"outputTokens\":50"));
        assertTrue(output.getOut().contains("\"totalTokens\":150"));
        assertTrue(output.getOut().contains("\"estimatedCostUsd\":0.00002266"));
        assertTrue(output.getOut().contains("\"providerLatencyMs\":42"));
        assertTrue(output.getOut().contains("\"pricingVersion\":\"pricing-test-v1\""));
        assertTrue(output.getOut().contains("\"timeoutMs\":30000"));
    }

    @Test
    void recordsProviderFailureWithoutLoggingPromptOrResponse(CapturedOutput output) {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class)))
                .thenThrow(new IllegalStateException("synthetic timeout"));

        AiProperties properties = new AiProperties(
                "bedrock", "model-v1", "us-east-1", "pricing-test-v1",
                BigDecimal.ZERO, BigDecimal.ZERO);

        assertThrows(IllegalStateException.class, () -> new BedrockConverseClient(client, properties).complete(
                "response-generation", "conversation.reply.generate", "secret system", "private user",
                128, 0.2f, "conversation-response-v1", "hash-v1"));

        assertTrue(output.getOut().contains("\"success\":false"));
        assertTrue(output.getOut().contains("\"errorType\":\"IllegalStateException\""));
        assertTrue(output.getOut().contains("\"promptVersion\":\"conversation-response-v1\""));
        assertTrue(!output.getOut().contains("secret system"));
        assertTrue(!output.getOut().contains("private user"));
    }

    @Test
    void sendsVersionedAgentModelInferenceAndContractMetadata(CapturedOutput output) {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.fromText("respuesta versionada"))
                        .build()))
                .usage(TokenUsage.builder().inputTokens(20).outputTokens(10).totalTokens(30).build())
                .build());
        AiProperties properties = new AiProperties(
                "bedrock", "global-model", "us-east-1", "pricing-test-v1",
                new BigDecimal("0.0721"), new BigDecimal("0.3090"));
        AgentRuntimeDefinition definition = new AgentRuntimeDefinition(
                "knowledge-specialist", 3, "Knowledge specialist", "Grounded support", "bedrock", "agent-model-v3",
                new AgentInferenceParameters(new BigDecimal("0.35"), new BigDecimal("0.72")), "prompt-v3",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "input-v3", "output-v3", Set.of("knowledge.retrieve"), Set.of("wcs-kb"),
                "memory-v1", "response-v1", Duration.ofSeconds(8), 2, 1000, 700,
                new BigDecimal("0.01"), null, "eval-v1");

        String result = new BedrockConverseClient(client, properties).completeForAgent(
                "response-generation", "conversation.reply.generate", "system", "user", 700, 0.35f, 0.72f,
                "prompt-v3", definition.systemPromptHash(), definition);

        assertEquals("respuesta versionada", result);
        org.mockito.ArgumentCaptor<ConverseRequest> request =
                org.mockito.ArgumentCaptor.forClass(ConverseRequest.class);
        org.mockito.Mockito.verify(client).converse(request.capture());
        assertEquals("agent-model-v3", request.getValue().modelId());
        assertEquals(700, request.getValue().inferenceConfig().maxTokens());
        assertEquals(0.35f, request.getValue().inferenceConfig().temperature());
        assertEquals(0.72f, request.getValue().inferenceConfig().topP());
        assertEquals(8_000L, request.getValue().overrideConfiguration().orElseThrow()
                .apiCallTimeout().orElseThrow().toMillis());
        assertTrue(output.getOut().contains("\"agentId\":\"knowledge-specialist\""));
        assertTrue(output.getOut().contains("\"agentVersion\":3"));
        assertTrue(output.getOut().contains("\"inputSchemaVersion\":\"input-v3\""));
        assertTrue(output.getOut().contains("\"outputSchemaVersion\":\"output-v3\""));
    }
}
