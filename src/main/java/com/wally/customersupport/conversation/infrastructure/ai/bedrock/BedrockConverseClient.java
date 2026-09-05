package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.wally.customersupport.shared.infrastructure.config.AiProperties;
import com.wally.customersupport.shared.infrastructure.observability.AiPricingCalculator;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

final class BedrockConverseClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(BedrockConverseClient.class);

    private final BedrockRuntimeClient client;
    private final AiProperties properties;
    private final String modelId;

    BedrockConverseClient(BedrockRuntimeClient client, AiProperties properties) {
        this.client = client;
        this.properties = properties;
        this.modelId = properties.effectiveModel();
    }

    String complete(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature) {
        Message message = Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(userPrompt))
                .build();
        ConverseRequest request = ConverseRequest.builder()
                .modelId(modelId)
                .system(SystemContentBlock.fromText(systemPrompt))
                .messages(message)
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(maxTokens)
                        .temperature(temperature)
                        .topP(0.9f)
                        .build())
                .build();

        ConverseResponse response = null;
        long startedAt = System.nanoTime();
        try {
            response = client.converse(request);
            if (response.output() == null || response.output().message() == null) {
                throw new IllegalStateException("Bedrock did not return a message");
            }
            String text = response.output().message().content().stream()
                    .map(ContentBlock::text)
                    .filter(value -> value != null && !value.isBlank())
                    .collect(Collectors.joining("\n"))
                    .trim();
            if (text.isBlank()) {
                throw new IllegalStateException("Bedrock returned an empty message");
            }
            recordUsage(stage, operation, response, true, null, startedAt);
            return text;
        } catch (RuntimeException exception) {
            recordUsage(stage, operation, response, false, exception.getClass().getSimpleName(), startedAt);
            throw exception;
        }
    }

    private void recordUsage(
            String stage,
            String operation,
            ConverseResponse response,
            boolean success,
            String errorType,
            long startedAt) {
        var usage = response == null ? null : response.usage();
        Integer inputTokensValue = usage == null ? null : usage.inputTokens();
        Integer outputTokensValue = usage == null ? null : usage.outputTokens();
        Integer totalTokensValue = usage == null ? null : usage.totalTokens();
        int inputTokens = inputTokensValue == null ? 0 : inputTokensValue;
        int outputTokens = outputTokensValue == null ? 0 : outputTokensValue;
        int totalTokens = totalTokensValue == null ? inputTokens + outputTokens : totalTokensValue;

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("stage", stage);
        fields.put("operation", operation);
        fields.put("provider", "bedrock");
        fields.put("model", modelId);
        fields.put("success", success);
        fields.put("tokenUsageAvailable", usage != null);
        fields.put("inputTokens", inputTokens);
        fields.put("outputTokens", outputTokens);
        fields.put("totalTokens", totalTokens);
        fields.put("estimatedCostUsd", AiPricingCalculator.estimatedCostUsd(
                inputTokens,
                outputTokens,
                properties.effectiveInputPriceUsdPerMillionTokens(),
                properties.effectiveOutputPriceUsdPerMillionTokens()));
        fields.put("pricingVersion", properties.effectivePricingVersion());
        fields.put("durationMs", elapsedMillis(startedAt));
        if (response != null && response.metrics() != null && response.metrics().latencyMs() != null) {
            fields.put("providerLatencyMs", response.metrics().latencyMs());
        }
        if (response != null && response.stopReason() != null) {
            fields.put("stopReason", response.stopReason().toString());
        }
        if (errorType != null) {
            fields.put("errorType", errorType);
        }

        if (success) {
            StructuredEventLog.info(LOGGER, "AI_USAGE_RECORDED", fields);
        } else {
            StructuredEventLog.warn(LOGGER, "AI_USAGE_RECORDED", fields);
        }
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
