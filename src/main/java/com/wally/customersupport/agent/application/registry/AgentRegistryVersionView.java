package com.wally.customersupport.agent.application.registry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import com.wally.customersupport.agent.domain.model.AgentLifecycleState;

/** Sanitized version metadata. Prompt contents and operator identities are intentionally absent. */
public record AgentRegistryVersionView(
        String agentId,
        int version,
        String name,
        String purpose,
        AgentLifecycleState state,
        String modelProvider,
        String modelId,
        BigDecimal temperature,
        BigDecimal topP,
        String systemPromptVersion,
        String systemPromptHash,
        String inputSchemaVersion,
        String outputSchemaVersion,
        Set<String> allowedTools,
        Set<String> knowledgeSources,
        String memoryPolicy,
        String responsePolicy,
        long timeoutMs,
        int maxSteps,
        int maxInputTokens,
        int maxOutputTokens,
        BigDecimal budgetLimitUsd,
        String fallbackAgentId,
        String evaluationSuiteVersion,
        Instant createdAt,
        Instant approvedAt) {
}
