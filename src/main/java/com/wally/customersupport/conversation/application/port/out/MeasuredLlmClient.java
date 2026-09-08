package com.wally.customersupport.conversation.application.port.out;

import java.math.BigDecimal;

/** Provider-neutral completion boundary that exposes safe operational metadata. */
public interface MeasuredLlmClient {

    LlmCompletion completeMeasured(
            String stage,
            String operation,
            String systemPrompt,
            String userPrompt,
            int maxTokens,
            float temperature);

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
