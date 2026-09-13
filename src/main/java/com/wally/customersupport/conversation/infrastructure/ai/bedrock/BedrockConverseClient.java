package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.conversation.application.port.out.MeasuredLlmClient;
import com.wally.customersupport.shared.infrastructure.config.AiProperties;
import com.wally.customersupport.shared.infrastructure.observability.AiPricingCalculator;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
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
        return completeMeasured(
                stage,
                operation,
                systemPrompt,
                userPrompt,
                maxTokens,
                temperature,
                null,
                null,
                modelId,
                0.9f,
                null,
                properties.effectiveRequestTimeout()).text();
    }

    String complete(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature,
            String promptVersion,
            String promptHash) {
        return completeMeasured(
                stage,
                operation,
                systemPrompt,
                userPrompt,
                maxTokens,
                temperature,
                promptVersion,
                promptHash,
                modelId,
                0.9f,
                null,
                properties.effectiveRequestTimeout()).text();
    }

    String completeForAgent(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature,
            float topP,
            String promptVersion,
            String promptHash,
            AgentRuntimeDefinition definition) {
        return completeMeasured(
                stage,
                operation,
                systemPrompt,
                userPrompt,
                maxTokens,
                temperature,
                promptVersion,
                promptHash,
                definition.modelId(),
                topP,
                definition,
                effectiveTimeout(definition.timeout())).text();
    }

    @Override
    public LlmCompletion completeMeasured(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature) {
        return completeMeasured(
                stage,
                operation,
                systemPrompt,
                userPrompt,
                maxTokens,
                temperature,
                null,
                null,
                modelId,
                0.9f,
                null,
                properties.effectiveRequestTimeout());
    }

    private LlmCompletion completeMeasured(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature,
            String promptVersion,
            String promptHash,
            String requestedModelId,
            float topP,
            AgentRuntimeDefinition definition,
            Duration requestTimeout) {
        Message message = Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(userPrompt))
                .build();
        ConverseRequest request = ConverseRequest.builder()
                .modelId(requestedModelId)
                .system(SystemContentBlock.fromText(systemPrompt))
                .messages(message)
                .overrideConfiguration(AwsRequestOverrideConfiguration.builder()
                        .apiCallTimeout(requestTimeout)
                        .build())
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(maxTokens)
                        .temperature(temperature)
                        .topP(topP)
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
            LlmCompletion completion = completion(text, response, startedAt, requestedModelId);
            recordUsage(
                    stage,
                    operation,
                    completion,
                    response.stopReason(),
                    true,
                    null,
                    promptVersion,
                    promptHash,
                    definition,
                    requestTimeout);
            return completion;
        } catch (RuntimeException exception) {
            LlmCompletion completion = response == null
                    ? null
                    : completion(null, response, startedAt, requestedModelId);
            recordUsage(
                    stage,
                    operation,
                    completion,
                    response == null ? null : response.stopReason(),
                    false,
                    exception.getClass().getSimpleName(),
                    promptVersion,
                    promptHash,
                    definition,
                    requestTimeout);
            throw exception;
        }
    }

    private LlmCompletion completion(
            String text,
            ConverseResponse response,
            long startedAt,
            String responseModelId) {
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
                responseModelId,
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
            String errorType,
            String promptVersion,
            String promptHash,
            AgentRuntimeDefinition definition,
            Duration requestTimeout) {

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("stage", stage);
        fields.put("operation", operation);
        fields.put("provider", "bedrock");
        fields.put("model", completion == null
                ? definition == null ? modelId : definition.modelId()
                : completion.modelId());
        fields.put("success", success);
        fields.put("tokenUsageAvailable", completion != null && completion.inputTokens() != null
                && completion.outputTokens() != null);
        fields.put("inputTokens", completion == null ? null : completion.inputTokens());
        fields.put("outputTokens", completion == null ? null : completion.outputTokens());
        fields.put("totalTokens", completion == null ? null : completion.totalTokens());
        fields.put("estimatedCostUsd", completion == null ? null : completion.estimatedCostUsd());
        fields.put("pricingVersion", completion == null ? properties.effectivePricingVersion() : completion.pricingVersion());
        fields.put("durationMs", completion == null ? null : completion.durationMs());
        fields.put("timeoutMs", requestTimeout.toMillis());
        if (completion != null && completion.providerLatencyMs() != null) {
            fields.put("providerLatencyMs", completion.providerLatencyMs());
        }
        if (stopReason != null) {
            fields.put("stopReason", stopReason.toString());
        }
        if (errorType != null) {
            fields.put("errorType", errorType);
        }
        if (promptVersion != null && !promptVersion.isBlank()) {
            fields.put("promptVersion", promptVersion);
        }
        if (promptHash != null && !promptHash.isBlank()) {
            fields.put("promptHash", promptHash);
        }
        if (definition != null) {
            fields.put("agentId", definition.agentId());
            fields.put("agentVersion", definition.agentVersion());
            fields.put("inputSchemaVersion", definition.inputSchemaVersion());
            fields.put("outputSchemaVersion", definition.outputSchemaVersion());
            fields.put("agentTimeoutMs", definition.timeout().toMillis());
            fields.put("agentMaxInputTokens", definition.maxInputTokens());
            fields.put("agentMaxOutputTokens", definition.maxOutputTokens());
            fields.put("agentBudgetLimitUsd", definition.budgetLimitUsd());
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

    private Duration effectiveTimeout(Duration agentTimeout) {
        return agentTimeout.compareTo(properties.effectiveRequestTimeout()) < 0
                ? agentTimeout
                : properties.effectiveRequestTimeout();
    }
}
