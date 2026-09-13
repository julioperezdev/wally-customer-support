package com.wally.customersupport.agent.infrastructure.http;

import java.math.BigDecimal;
import java.util.Set;

import com.wally.customersupport.agent.application.registry.AgentVersionDraftCommand;

/** HTTP authoring input; prompt and schema contents are never accepted here. */
public record AgentVersionDraftHttpRequest(
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

    AgentVersionDraftCommand toCommand(String agentId) {
        return new AgentVersionDraftCommand(
                agentId,
                version,
                name,
                purpose,
                modelProvider,
                modelId,
                temperature,
                topP,
                systemPromptVersion,
                systemPromptHash,
                inputSchemaVersion,
                outputSchemaVersion,
                allowedTools,
                knowledgeSources,
                memoryPolicy,
                responsePolicy,
                timeoutMs,
                maxSteps,
                maxInputTokens,
                maxOutputTokens,
                budgetLimitUsd,
                fallbackAgentId,
                evaluationSuiteVersion);
    }
}
