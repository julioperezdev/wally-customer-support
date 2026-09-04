package com.wally.customersupport.adapter.out.ai.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import com.wally.customersupport.infrastructure.config.AiProperties;
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
    }
}
