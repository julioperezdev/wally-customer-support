package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.conversation.application.port.out.MeasuredLlmClient;
import com.wally.customersupport.conversation.application.tool.ConversationRouteToolContract;
import com.wally.customersupport.shared.infrastructure.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import software.amazon.awssdk.core.document.Document;
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
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

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

    @Test
    void usesGptOssRouterWithHighReasoningAndIndependentPricing(CapturedOutput output) {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        when(client.converse(any(ConverseRequest.class))).thenReturn(ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.fromText("{\"intent\":\"GREETING\"}"))
                        .build()))
                .usage(TokenUsage.builder().inputTokens(100).outputTokens(50).totalTokens(150).build())
                .build());
        AiProperties properties = new AiProperties(
                "bedrock",
                "openai.gpt-oss-20b-1:0",
                "us-east-1",
                "pricing-default-v1",
                new BigDecimal("0.0721"),
                new BigDecimal("0.3090"),
                Duration.ofSeconds(30),
                false,
                new AiProperties.Router(
                        "openai.gpt-oss-20b-1:0",
                        "conversation-router-v3",
                        "aws-bedrock-us-east-1-standard-2026-09",
                        new BigDecimal("0.0721"),
                        new BigDecimal("0.3090"),
                        "high"));

        String result = new BedrockConverseClient(client, properties).completeForRouter(
                "intent-classification",
                "conversation.intent.classify",
                "system",
                "user",
                512,
                0.0f,
                "conversation-intent-v4",
                "hash-v4");

        assertEquals("{\"intent\":\"GREETING\"}", result);
        org.mockito.ArgumentCaptor<ConverseRequest> request =
                org.mockito.ArgumentCaptor.forClass(ConverseRequest.class);
        org.mockito.Mockito.verify(client).converse(request.capture());
        assertEquals("openai.gpt-oss-20b-1:0", request.getValue().modelId());
        assertEquals("high", request.getValue().additionalModelRequestFields()
                .asMap().get("reasoning_effort").asString());
        assertTrue(output.getOut().contains("\"model\":\"openai.gpt-oss-20b-1:0\""));
        assertTrue(output.getOut().contains("\"agentId\":\"conversation-router\""));
        assertTrue(output.getOut().contains("\"agentVersion\":\"conversation-router-v3\""));
        assertTrue(output.getOut().contains("\"reasoningEffort\":\"high\""));
        assertTrue(output.getOut().contains("\"estimatedCostUsd\":0.000022660000"));
        assertTrue(output.getOut().contains(
                "\"pricingVersion\":\"aws-bedrock-us-east-1-standard-2026-09\""));
    }

    @Test
    void sendsBoundedToolSchemaAndExtractsStructuredToolInput(CapturedOutput output) {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        Map<String, Document> route = new LinkedHashMap<>();
        route.put("intent", Document.fromString("CATALOG_SEARCH"));
        route.put("action", Document.fromString("CATALOG_SEARCH"));
        route.put("confidence", Document.fromNumber(new BigDecimal("0.94")));
        route.put("quantity", Document.fromNumber(BigDecimal.ONE));
        route.put("catalogQuery", Document.fromMap(Map.of(
                "name", Document.fromString("buzo"),
                "size", Document.fromString("M"))));
        route.put("policyKey", Document.fromNull());
        route.put("missingParameters", Document.fromList(java.util.List.of()));
        when(client.converse(any(ConverseRequest.class))).thenReturn(ConverseResponse.builder()
                .output(ConverseOutput.fromMessage(Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(ContentBlock.fromToolUse(ToolUseBlock.builder()
                                .toolUseId("tool-use-1")
                                .name("conversation_route")
                                .input(Document.fromMap(route))
                                .build()))
                        .build()))
                .usage(TokenUsage.builder().inputTokens(30).outputTokens(20).totalTokens(50).build())
                .stopReason(StopReason.TOOL_USE)
                .build());

        AiProperties properties = new AiProperties(
                "bedrock", "openai.gpt-oss-20b-1:0", "us-east-1", "pricing-test-v1",
                new BigDecimal("0.0721"), new BigDecimal("0.3090"), Duration.ofSeconds(10), true);

        BedrockConverseClient.ToolUseCompletion result = new BedrockConverseClient(client, properties)
                .completeWithToolUse(
                        "intent-classification", "conversation.intent.classify", "system", "user",
                        512, 0.0f, "conversation-intent-v4", "hash-v4",
                        ConversationRouteToolContract.DESCRIPTOR);

        assertEquals(ConversationRouteToolContract.NAME, result.toolName());
        assertTrue(result.inputJson().contains("\"intent\":\"CATALOG_SEARCH\""));
        assertTrue(output.getOut().contains("\"eventType\":\"AI_TOOL_CALL_PROPOSED\""));
        assertTrue(output.getOut().contains("\"inputFieldCount\":7"));
        assertTrue(!output.getOut().contains("\"name\":\"buzo\""));

        org.mockito.ArgumentCaptor<ConverseRequest> request =
                org.mockito.ArgumentCaptor.forClass(ConverseRequest.class);
        org.mockito.Mockito.verify(client).converse(request.capture());
        assertEquals("conversation_route",
                request.getValue().toolConfig().tools().getFirst().toolSpec().name());
        assertTrue(request.getValue().toolConfig().toolChoice() == null);
        assertTrue(request.getValue().toolConfig().tools().getFirst().toolSpec()
                .inputSchema().json().isMap());
        assertEquals("bedrock", result.completion().provider());
        assertEquals(50, result.completion().totalTokens());
    }
}
