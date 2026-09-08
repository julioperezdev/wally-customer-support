package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.wally.customersupport.conversation.application.port.out.MeasuredLlmClient;
import com.wally.customersupport.shared.infrastructure.config.AiProperties;
import com.wally.customersupport.shared.infrastructure.observability.AiPricingCalculator;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;

@Slf4j
final class BedrockConverseClient implements MeasuredLlmClient {

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
        return completeMeasured(stage, operation, systemPrompt, userPrompt, maxTokens, temperature).text();
    }

    @Override
    public LlmCompletion completeMeasured(
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
            LlmCompletion completion = completion(text, response, startedAt);
            recordUsage(stage, operation, completion, response.stopReason(), true, null);
            return completion;
        } catch (RuntimeException exception) {
            LlmCompletion completion = response == null ? null : completion(null, response, startedAt);
            recordUsage(
                    stage,
                    operation,
                    completion,
                    response == null ? null : response.stopReason(),
                    false,
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private LlmCompletion completion(String text, ConverseResponse response, long startedAt) {
        var usage = response == null ? null : response.usage();
        Integer inputTokens = usage == null ? null : usage.inputTokens();
        Integer outputTokens = usage == null ? null : usage.outputTokens();
        Integer totalTokens = usage == null ? null : usage.totalTokens();
        Long providerLatency = response == null || response.metrics() == null
                ? null
                : response.metrics().latencyMs();
        var estimatedCost = inputTokens == null || outputTokens == null
                ? null
                : AiPricingCalculator.estimatedCostUsd(
                        inputTokens,
                        outputTokens,
                        properties.effectiveInputPriceUsdPerMillionTokens(),
                        properties.effectiveOutputPriceUsdPerMillionTokens());
        return new LlmCompletion(
                text,
                "bedrock",
                modelId,
                elapsedMillis(startedAt),
                providerLatency,
                inputTokens,
                outputTokens,
                totalTokens,
                estimatedCost,
                properties.effectivePricingVersion());
    }

    private void recordUsage(
            String stage,
            String operation,
            LlmCompletion completion,
            StopReason stopReason,
            boolean success,
            String errorType) {

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("stage", stage);
        fields.put("operation", operation);
        fields.put("provider", "bedrock");
        fields.put("model", modelId);
        fields.put("success", success);
        fields.put("tokenUsageAvailable", completion != null && completion.inputTokens() != null
                && completion.outputTokens() != null);
        fields.put("inputTokens", completion == null ? null : completion.inputTokens());
        fields.put("outputTokens", completion == null ? null : completion.outputTokens());
        fields.put("totalTokens", completion == null ? null : completion.totalTokens());
        fields.put("estimatedCostUsd", completion == null ? null : completion.estimatedCostUsd());
        fields.put("pricingVersion", completion == null ? properties.effectivePricingVersion() : completion.pricingVersion());
        fields.put("durationMs", completion == null ? null : completion.durationMs());
        if (completion != null && completion.providerLatencyMs() != null) {
            fields.put("providerLatencyMs", completion.providerLatencyMs());
        }
        if (stopReason != null) {
            fields.put("stopReason", stopReason.toString());
        }
        if (errorType != null) {
            fields.put("errorType", errorType);
        }

        if (success) {
            StructuredEventLog.info(log, "AI_USAGE_RECORDED", fields);
        } else {
            StructuredEventLog.warn(log, "AI_USAGE_RECORDED", fields);
        }
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
