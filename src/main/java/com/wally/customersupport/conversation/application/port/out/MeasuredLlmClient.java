package com.wally.customersupport.conversation.application.port.out;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;

/** Provider-neutral completion boundary that exposes safe operational metadata. */
public interface MeasuredLlmClient {

    LlmCompletion completeMeasured(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature);

    /** Executes from a pinned SQL profile; adapters that cannot honor it fail closed. */
    default LlmCompletion completeMeasuredForAgent(
            String stage,
            String operation,
            String userPrompt,
            AgentRuntimeDefinition definition,
            Duration maxRequestTimeout,
            String correlationId) {
        Objects.requireNonNull(definition, "definition");
        throw new UnsupportedOperationException("profiled Bedrock execution is not supported by this adapter");
    }

    record LlmCompletion(
            String text,
            String provider,
            String modelId,
            long durationMs,
            Long providerLatencyMs,
            Integer inputTokens,
            Integer outputTokens,
            Integer totalTokens,
            BigDecimal estimatedCostUsd,
            String pricingVersion) {
    }
}
