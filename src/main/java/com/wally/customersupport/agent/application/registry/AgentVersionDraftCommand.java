package com.wally.customersupport.agent.application.registry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.agent.domain.model.AgentVersion;

/** Authoring command for a new immutable draft definition. */
public record AgentVersionDraftCommand(
        String agentId,
        Integer version,
        String name,
        String purpose,
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
        Long timeoutMs,
        Integer maxSteps,
        Integer maxInputTokens,
        Integer maxOutputTokens,
        BigDecimal budgetLimitUsd,
        String fallbackAgentId,
        String evaluationSuiteVersion) {

    public AgentVersion toDraft(int resolvedVersion, String actor, Instant createdAt) {
        return AgentVersion.draft(
                agentId,
                resolvedVersion,
                name,
                purpose,
                modelProvider,
                modelId,
                new AgentInferenceParameters(temperature, topP),
                systemPromptVersion,
                systemPromptHash,
                inputSchemaVersion,
                outputSchemaVersion,
                allowedTools,
                knowledgeSources,
                memoryPolicy,
                responsePolicy,
                Duration.ofMillis(timeoutMs),
                maxSteps,
                maxInputTokens,
                maxOutputTokens,
                budgetLimitUsd,
                fallbackAgentId,
                evaluationSuiteVersion,
                actor,
                createdAt);
    }
}
