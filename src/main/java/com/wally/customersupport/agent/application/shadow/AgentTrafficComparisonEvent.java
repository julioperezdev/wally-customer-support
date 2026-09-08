package com.wally.customersupport.agent.application.shadow;

import java.math.BigDecimal;

/** Sanitized comparison event contract; it never carries prompts or message content. */
public record AgentTrafficComparisonEvent(
        String requestId,
        String pseudonymizedConversationId,
        String channel,
        String useCase,
        String agentId,
        int agentVersion,
        String modelProvider,
        String modelId,
        AgentTrafficMode mode,
        String outcome,
        long latencyMs,
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        BigDecimal estimatedCostUsd,
        String fallbackReason,
        boolean candidateResponsePublished) {
}
