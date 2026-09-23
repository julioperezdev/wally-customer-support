package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.conversation.application.port.out.MeasuredLlmClient;
import com.wally.customersupport.conversation.application.tool.WcsToolDescriptor;
import com.wally.customersupport.shared.infrastructure.config.AiProperties;
import com.wally.customersupport.shared.infrastructure.observability.AiPricingCalculator;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.SpecificToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

@Slf4j
final class BedrockConverseClient implements MeasuredLlmClient {

    private final BedrockRuntimeClient client;
    private final AiProperties properties;
    private final ObjectMapper objectMapper;

    BedrockConverseClient(BedrockRuntimeClient client, AiProperties properties) {
        this(client, properties, new ObjectMapper());
    }

    BedrockConverseClient(BedrockRuntimeClient client, AiProperties properties, ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    ToolUseCompletion completeWithToolUse(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature,
            String promptVersion,
            String promptHash,
            WcsToolDescriptor descriptor) {
        return completeWithToolUse(
                stage, operation, systemPrompt, userPrompt, maxTokens, temperature,
                promptVersion, promptHash, null, descriptor, properties.effectiveDefaultModelSettings());
    }

    ToolUseCompletion completeWithToolUse(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature,
            String promptVersion,
            String promptHash,
            String correlationId,
            WcsToolDescriptor descriptor) {
        return completeWithToolUse(
                stage, operation, systemPrompt, userPrompt, maxTokens, temperature,
                promptVersion, promptHash, correlationId, descriptor,
                properties.effectiveDefaultModelSettings());
    }

    ToolUseCompletion completeWithToolUseForRouter(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature,
            String promptVersion,
            String promptHash,
            WcsToolDescriptor descriptor) {
        return completeWithToolUse(
                stage, operation, systemPrompt, userPrompt, maxTokens, temperature,
                promptVersion, promptHash, null, descriptor, properties.effectiveRouterModelSettings());
    }

    ToolUseCompletion completeWithToolUseForRouter(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature,
            String promptVersion,
            String promptHash,
            String correlationId,
            WcsToolDescriptor descriptor) {
        return completeWithToolUse(
                stage, operation, systemPrompt, userPrompt, maxTokens, temperature,
                promptVersion, promptHash, correlationId, descriptor,
                properties.effectiveRouterModelSettings());
    }

    ToolUseCompletion completeWithToolUseForAgent(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature,
            float topP,
            String promptVersion,
            String promptHash,
            WcsToolDescriptor descriptor,
            AgentRuntimeDefinition definition) {
        return completeWithToolUse(
                stage, operation, systemPrompt, userPrompt, maxTokens, temperature,
                promptVersion, promptHash, null, descriptor, agentModelSettings(definition),
                definition, topP, effectiveTimeout(definition.timeout()));
    }

    ToolUseCompletion completeWithToolUseForAgent(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature,
            float topP,
            String promptVersion,
            String promptHash,
            WcsToolDescriptor descriptor,
            AgentRuntimeDefinition definition,
            Duration maxRequestTimeout) {
        return completeWithToolUse(
                stage, operation, systemPrompt, userPrompt, maxTokens, temperature,
                promptVersion, promptHash, null, descriptor, agentModelSettings(definition),
                definition, topP, effectiveTimeout(definition.timeout(), maxRequestTimeout));
    }

    ToolUseCompletion completeWithToolUseForAgent(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature,
            float topP,
            String promptVersion,
            String promptHash,
            String correlationId,
            WcsToolDescriptor descriptor,
            AgentRuntimeDefinition definition) {
        return completeWithToolUse(
                stage, operation, systemPrompt, userPrompt, maxTokens, temperature,
                promptVersion, promptHash, correlationId, descriptor, agentModelSettings(definition),
                definition, topP, effectiveTimeout(definition.timeout()));
    }

    private ToolUseCompletion completeWithToolUse(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature,
            String promptVersion,
            String promptHash,
            String correlationId,
            WcsToolDescriptor descriptor,
            AiProperties.ModelSettings modelSettings) {
        return completeWithToolUse(stage, operation, systemPrompt, userPrompt, maxTokens, temperature,
                promptVersion, promptHash, correlationId, descriptor, modelSettings, null, 0.9f,
                properties.effectiveRequestTimeout());
    }

    private ToolUseCompletion completeWithToolUse(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature,
            String promptVersion,
            String promptHash,
            String correlationId,
            WcsToolDescriptor descriptor,
            AiProperties.ModelSettings modelSettings,
            AgentRuntimeDefinition definition,
            float topP,
            Duration requestTimeout) {
        Objects.requireNonNull(descriptor, "descriptor");
        String providerToolName = providerToolName(descriptor.name());
        Message message = Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(userPrompt))
                .build();
        ToolConfiguration.Builder toolConfigurationBuilder = ToolConfiguration.builder()
                .tools(Tool.builder()
                        .toolSpec(ToolSpecification.builder()
                                .name(providerToolName)
                                .description(descriptor.description())
                                .inputSchema(ToolInputSchema.builder()
                                        .json(toDocument(descriptor.inputSchemaJson()))
                                        .build())
                                .build())
                        .build())
                ;
        if (supportsSpecificToolChoice(modelSettings.modelId())) {
            toolConfigurationBuilder.toolChoice(choice(providerToolName));
        }
        ToolConfiguration toolConfiguration = toolConfigurationBuilder.build();
        ConverseRequest.Builder requestBuilder = ConverseRequest.builder()
                .modelId(modelSettings.modelId())
                .system(SystemContentBlock.fromText(systemPrompt))
                .messages(message)
                .toolConfig(toolConfiguration)
                .overrideConfiguration(AwsRequestOverrideConfiguration.builder()
                        .apiCallTimeout(requestTimeout)
                        .build())
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(maxTokens)
                        .temperature(temperature)
                        .topP(topP)
                        .build());
        Document additionalModelRequestFields = additionalModelRequestFields(modelSettings, definition);
        if (additionalModelRequestFields != null) {
            requestBuilder.additionalModelRequestFields(additionalModelRequestFields);
        }
        ConverseRequest request = requestBuilder.build();

        ConverseResponse response = null;
        long startedAt = System.nanoTime();
        try {
            response = client.converse(request);
            ToolUseBlock toolUse = response.output() == null || response.output().message() == null
                    ? null
                    : response.output().message().content().stream()
                            .map(ContentBlock::toolUse)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null);
            if (toolUse == null || !providerToolName.equals(toolUse.name()) || toolUse.input() == null) {
                throw new IllegalStateException("Bedrock did not return the requested tool call");
            }
            String inputJson = objectMapper.writeValueAsString(toolUse.input().unwrap());
            LlmCompletion completion = completion(null, response, startedAt, modelSettings);
            recordUsage(stage, operation, completion, response.stopReason(), true, null,
                    promptVersion, promptHash, correlationId, definition, requestTimeout, modelSettings);
            recordToolCall(stage, operation, descriptor, inputJson, correlationId, true, null);
            return new ToolUseCompletion(descriptor.name(), inputJson, completion);
        } catch (RuntimeException exception) {
            LlmCompletion completion = response == null
                    ? null
                    : completion(null, response, startedAt, modelSettings);
            recordUsage(stage, operation, completion,
                    response == null ? null : response.stopReason(), false,
                    exception.getClass().getSimpleName(), promptVersion, promptHash,
                    correlationId, definition, requestTimeout, modelSettings);
            recordToolCall(stage, operation, descriptor, null, correlationId, false,
                    exception.getClass().getSimpleName());
            throw exception;
        }
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
                null,
                properties.effectiveDefaultModelSettings(),
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
            String promptHash,
            String correlationId) {
        return completeMeasured(
                stage, operation, systemPrompt, userPrompt, maxTokens, temperature,
                promptVersion, promptHash, correlationId, properties.effectiveDefaultModelSettings(), 0.9f, null,
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
                null,
                properties.effectiveDefaultModelSettings(),
                0.9f,
                null,
                properties.effectiveRequestTimeout()).text();
    }

    String completeForRouter(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature,
            String promptVersion,
            String promptHash) {
        return completeMeasured(
                stage, operation, systemPrompt, userPrompt, maxTokens, temperature,
                promptVersion, promptHash, null, properties.effectiveRouterModelSettings(), 0.9f,
                null, properties.effectiveRequestTimeout()).text();
    }

    String completeForRouter(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature,
            String promptVersion,
            String promptHash,
            String correlationId) {
        return completeMeasured(
                stage, operation, systemPrompt, userPrompt, maxTokens, temperature,
                promptVersion, promptHash, correlationId, properties.effectiveRouterModelSettings(), 0.9f,
                null, properties.effectiveRequestTimeout()).text();
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
                null,
                agentModelSettings(definition),
                topP,
                definition,
                effectiveTimeout(definition.timeout())).text();
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
            AgentRuntimeDefinition definition,
            String correlationId) {
        return completeMeasured(
                stage, operation, systemPrompt, userPrompt, maxTokens, temperature,
                promptVersion, promptHash, correlationId, agentModelSettings(definition), topP,
                definition, effectiveTimeout(definition.timeout())).text();
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
                null,
                properties.effectiveDefaultModelSettings(),
                0.9f,
                null,
                properties.effectiveRequestTimeout());
    }

    @Override
    public LlmCompletion completeMeasuredForAgent(
            String stage,
            String operation,
            String userPrompt,
            AgentRuntimeDefinition definition,
            Duration maxRequestTimeout,
            String correlationId) {
        Objects.requireNonNull(definition, "definition");
        var invocation = definition.invocationConfiguration();
        return completeMeasured(
                stage,
                operation,
                invocation.systemPrompt(),
                userPrompt,
                definition.maxOutputTokens(),
                definition.inferenceParameters().temperature().floatValue(),
                definition.semanticVersion(),
                definition.systemPromptHash(),
                correlationId,
                agentModelSettings(definition),
                definition.inferenceParameters().topP().floatValue(),
                definition,
                effectiveTimeout(definition.timeout(), maxRequestTimeout));
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
            String correlationId,
            AiProperties.ModelSettings modelSettings,
            float topP,
            AgentRuntimeDefinition definition,
            Duration requestTimeout) {
        Message message = Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(userPrompt))
                .build();
        ConverseRequest.Builder requestBuilder = ConverseRequest.builder()
                .modelId(modelSettings.modelId())
                .system(SystemContentBlock.fromText(systemPrompt))
                .messages(message)
                .overrideConfiguration(AwsRequestOverrideConfiguration.builder()
                        .apiCallTimeout(requestTimeout)
                        .build())
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(maxTokens)
                        .temperature(temperature)
                        .topP(topP)
                        .build());
        Document additionalModelRequestFields = additionalModelRequestFields(modelSettings, definition);
        if (additionalModelRequestFields != null) {
            requestBuilder.additionalModelRequestFields(additionalModelRequestFields);
        }
        ConverseRequest request = requestBuilder.build();

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
            LlmCompletion completion = completion(text, response, startedAt, modelSettings);
            recordUsage(
                    stage,
                    operation,
                    completion,
                    response.stopReason(),
                    true,
                    null,
                    promptVersion,
                    promptHash,
                    correlationId,
                    definition,
                    requestTimeout,
                    modelSettings);
            return completion;
        } catch (RuntimeException exception) {
            LlmCompletion completion = response == null
                    ? null
                    : completion(null, response, startedAt, modelSettings);
            recordUsage(
                    stage,
                    operation,
                    completion,
                    response == null ? null : response.stopReason(),
                    false,
                    exception.getClass().getSimpleName(),
                    promptVersion,
                    promptHash,
                    correlationId,
                    definition,
                    requestTimeout,
                    modelSettings);
            throw exception;
        }
    }

    private LlmCompletion completion(
            String text,
            ConverseResponse response,
            long startedAt,
            AiProperties.ModelSettings modelSettings) {
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
                        modelSettings.inputPriceUsdPerMillionTokens(),
                        modelSettings.outputPriceUsdPerMillionTokens());
        return new LlmCompletion(
                text,
                "bedrock",
                modelSettings.modelId(),
                elapsedMillis(startedAt),
                providerLatency,
                inputTokens,
                outputTokens,
                totalTokens,
                estimatedCost,
                modelSettings.pricingVersion());
    }

    private Document additionalModelRequestFields(
            AiProperties.ModelSettings modelSettings,
            AgentRuntimeDefinition definition) {
        if (modelSettings.modelId() == null || !modelSettings.modelId().startsWith("openai.gpt-oss-")) {
            return null;
        }
        String effort = reasoningEffort(modelSettings, definition);
        if (effort == null) {
            return null;
        }
        return Document.fromMap(Map.of(
                "reasoning_effort",
                Document.fromString(effort)));
    }

    private String reasoningEffort(
            AiProperties.ModelSettings modelSettings,
            AgentRuntimeDefinition definition) {
        if (definition != null) {
            return definition.invocationConfiguration().reasoningEffort();
        }
        return "conversation-router".equals(modelSettings.agentId())
                ? properties.effectiveRouterReasoningEffort()
                : null;
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
            String correlationId,
            AgentRuntimeDefinition definition,
            Duration requestTimeout,
            AiProperties.ModelSettings modelSettings) {

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("stage", stage);
        fields.put("operation", operation);
        fields.put("provider", "bedrock");
        if (correlationId != null && !correlationId.isBlank()) {
            fields.put("correlationId", correlationId);
        }
        fields.put("model", completion == null
                ? modelSettings.modelId()
                : completion.modelId());
        fields.put("success", success);
        fields.put("tokenUsageAvailable", completion != null && completion.inputTokens() != null
                && completion.outputTokens() != null);
        fields.put("inputTokens", completion == null ? null : completion.inputTokens());
        fields.put("outputTokens", completion == null ? null : completion.outputTokens());
        fields.put("totalTokens", completion == null ? null : completion.totalTokens());
        fields.put("estimatedCostUsd", completion == null ? null : completion.estimatedCostUsd());
        fields.put("pricingVersion", completion == null ? modelSettings.pricingVersion() : completion.pricingVersion());
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
            fields.put("agentSemanticVersion", definition.semanticVersion());
            fields.put("inputSchemaVersion", definition.inputSchemaVersion());
            fields.put("outputSchemaVersion", definition.outputSchemaVersion());
            fields.put("agentTimeoutMs", definition.timeout().toMillis());
            fields.put("agentMaxInputTokens", definition.maxInputTokens());
            fields.put("agentMaxOutputTokens", definition.maxOutputTokens());
            fields.put("agentBudgetLimitUsd", definition.budgetLimitUsd());
        }
        if (modelSettings.agentId() != null) {
            fields.put("agentId", modelSettings.agentId());
            fields.put("agentVersion", definition == null
                    ? modelSettings.agentVersion()
                    : definition.agentVersion());
        }
        String reasoningEffort = reasoningEffort(modelSettings, definition);
        if (reasoningEffort != null) {
            fields.put("reasoningEffort", reasoningEffort);
        }

        if (success) {
            StructuredEventLog.info(log, "AI_USAGE_RECORDED", fields);
        } else {
            StructuredEventLog.warn(log, "AI_USAGE_RECORDED", fields);
        }
    }

    private void recordToolCall(
            String stage,
            String operation,
            WcsToolDescriptor descriptor,
            String inputJson,
            String correlationId,
            boolean success,
            String errorType) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("stage", stage);
        fields.put("operation", operation);
        fields.put("toolName", descriptor.name());
        fields.put("providerToolName", providerToolName(descriptor.name()));
        fields.put("inputSchemaVersion", descriptor.inputSchemaVersion());
        fields.put("outputSchemaVersion", descriptor.outputSchemaVersion());
        fields.put("requiredCapability", descriptor.requiredCapability());
        fields.put("success", success);
        if (correlationId != null && !correlationId.isBlank()) {
            fields.put("correlationId", correlationId);
        }
        if (inputJson != null) {
            try {
                JsonNode input = objectMapper.readTree(inputJson);
                List<String> keys = new ArrayList<>();
                input.propertyNames().forEach(keys::add);
                fields.put("inputFieldCount", keys.size());
                fields.put("inputFields", keys);
            } catch (RuntimeException ignored) {
                fields.put("inputFieldCount", null);
            }
        }
        if (errorType != null) {
            fields.put("errorType", errorType);
        }
        if (success) {
            StructuredEventLog.info(log, "AI_TOOL_CALL_PROPOSED", fields);
        } else {
            StructuredEventLog.warn(log, "AI_TOOL_CALL_FAILED", fields);
        }
    }

    private static software.amazon.awssdk.services.bedrockruntime.model.ToolChoice choice(String name) {
        return software.amazon.awssdk.services.bedrockruntime.model.ToolChoice.builder()
                .tool(SpecificToolChoice.builder().name(name).build())
                .build();
    }

    private static boolean supportsSpecificToolChoice(String modelId) {
        return modelId != null
                && (modelId.startsWith("anthropic.claude-3") || modelId.startsWith("amazon.nova"));
    }

    private static String providerToolName(String logicalName) {
        String normalized = logicalName.replace('.', '_');
        if (!normalized.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Tool name is not valid for Bedrock Converse");
        }
        return normalized;
    }

    private Document toDocument(String json) {
        try {
            return toDocument(objectMapper.readTree(json));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid tool input schema", exception);
        }
    }

    private Document toDocument(JsonNode node) {
        if (node == null || node.isNull()) {
            return Document.fromNull();
        }
        if (node.isObject()) {
            Map<String, Document> values = new LinkedHashMap<>();
            node.properties().forEach(entry -> values.put(entry.getKey(), toDocument(entry.getValue())));
            return Document.fromMap(values);
        }
        if (node.isArray()) {
            List<Document> values = new ArrayList<>();
            node.forEach(value -> values.add(toDocument(value)));
            return Document.fromList(values);
        }
        if (node.isBoolean()) {
            return Document.fromBoolean(node.booleanValue());
        }
        if (node.isNumber()) {
            return Document.fromNumber(node.decimalValue());
        }
        return Document.fromString(node.asText());
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private Duration effectiveTimeout(Duration agentTimeout) {
        return agentTimeout.compareTo(properties.effectiveRequestTimeout()) < 0
                ? agentTimeout
                : properties.effectiveRequestTimeout();
    }

    private Duration effectiveTimeout(Duration agentTimeout, Duration executionCap) {
        Duration effective = effectiveTimeout(agentTimeout);
        return executionCap == null || executionCap.isZero() || executionCap.isNegative()
                || effective.compareTo(executionCap) <= 0
                ? effective
                : executionCap;
    }

    private AiProperties.ModelSettings agentModelSettings(AgentRuntimeDefinition definition) {
        var invocation = definition.invocationConfiguration();
        return new AiProperties.ModelSettings(
                definition.modelId(),
                definition.systemPromptVersion(),
                invocation.pricingVersion() == null
                        ? properties.effectivePricingVersion()
                        : invocation.pricingVersion(),
                invocation.inputPriceUsdPerMillionTokens() == null
                        ? properties.effectiveInputPriceUsdPerMillionTokens()
                        : invocation.inputPriceUsdPerMillionTokens(),
                invocation.outputPriceUsdPerMillionTokens() == null
                        ? properties.effectiveOutputPriceUsdPerMillionTokens()
                        : invocation.outputPriceUsdPerMillionTokens(),
                definition.agentId(),
                Integer.toString(definition.agentVersion()));
    }

    record ToolUseCompletion(String toolName, String inputJson, LlmCompletion completion) {
    }
}
