package com.wally.customersupport.agent.application.registry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import com.wally.customersupport.agent.domain.model.AgentInvocationConfiguration;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;

/** Version definition returned only through the authenticated control plane. */
public record AgentRegistryVersionView(
        String agentId,
        int version,
        String semanticVersion,
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
        Instant approvedAt,
        AgentInvocationConfiguration invocationConfiguration) {

    public AgentRegistryVersionView(
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
        this(agentId, version, "1.0.0", name, purpose, state, modelProvider, modelId, temperature, topP,
                systemPromptVersion, systemPromptHash, inputSchemaVersion, outputSchemaVersion,
                allowedTools, knowledgeSources, memoryPolicy, responsePolicy, timeoutMs, maxSteps,
                maxInputTokens, maxOutputTokens, budgetLimitUsd, fallbackAgentId, evaluationSuiteVersion,
                createdAt, approvedAt, AgentInvocationConfiguration.empty());
    }
}
