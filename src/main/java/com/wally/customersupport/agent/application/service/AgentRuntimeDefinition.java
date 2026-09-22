package com.wally.customersupport.agent.application.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.agent.domain.model.AgentInvocationConfiguration;
import com.wally.customersupport.agent.domain.model.AgentVersion;

/**
 * Immutable execution snapshot derived from a published agent version.
 * It contains executable prompt configuration but never conversation data;
 * operational telemetry records only prompt identifiers and hashes.
 */
public record AgentRuntimeDefinition(
        String agentId,
        int agentVersion,
        String name,
        String purpose,
        String modelProvider,
        String modelId,
        AgentInferenceParameters inferenceParameters,
        String systemPromptVersion,
        String systemPromptHash,
        String inputSchemaVersion,
        String outputSchemaVersion,
        Set<String> allowedTools,
        Set<String> knowledgeSources,
        String memoryPolicy,
        String responsePolicy,
        Duration timeout,
        int maxSteps,
        int maxInputTokens,
        int maxOutputTokens,
        BigDecimal budgetLimitUsd,
        String fallbackAgentId,
        String evaluationSuiteVersion,
        String semanticVersion,
        AgentInvocationConfiguration invocationConfiguration) {

    public AgentRuntimeDefinition(
            String agentId,
            int agentVersion,
            String name,
            String purpose,
            String modelProvider,
            String modelId,
            AgentInferenceParameters inferenceParameters,
            String systemPromptVersion,
            String systemPromptHash,
            String inputSchemaVersion,
            String outputSchemaVersion,
            Set<String> allowedTools,
            Set<String> knowledgeSources,
            String memoryPolicy,
            String responsePolicy,
            Duration timeout,
            int maxSteps,
            int maxInputTokens,
            int maxOutputTokens,
            BigDecimal budgetLimitUsd,
            String fallbackAgentId,
            String evaluationSuiteVersion) {
        this(agentId, agentVersion, name, purpose, modelProvider, modelId, inferenceParameters,
                systemPromptVersion, systemPromptHash, inputSchemaVersion, outputSchemaVersion,
                allowedTools, knowledgeSources, memoryPolicy, responsePolicy, timeout, maxSteps,
                maxInputTokens, maxOutputTokens, budgetLimitUsd, fallbackAgentId, evaluationSuiteVersion,
                "1.0.0", AgentInvocationConfiguration.empty());
    }

    public AgentRuntimeDefinition {
        agentId = required(agentId, "agentId");
        name = required(name, "name");
        purpose = required(purpose, "purpose");
        modelProvider = required(modelProvider, "modelProvider");
        modelId = required(modelId, "modelId");
        inferenceParameters = Objects.requireNonNull(inferenceParameters, "inferenceParameters");
        systemPromptVersion = required(systemPromptVersion, "systemPromptVersion");
        systemPromptHash = required(systemPromptHash, "systemPromptHash");
        inputSchemaVersion = required(inputSchemaVersion, "inputSchemaVersion");
        outputSchemaVersion = required(outputSchemaVersion, "outputSchemaVersion");
        allowedTools = immutableSet(allowedTools, "allowedTools");
        knowledgeSources = immutableSet(knowledgeSources, "knowledgeSources");
        memoryPolicy = required(memoryPolicy, "memoryPolicy");
        responsePolicy = required(responsePolicy, "responsePolicy");
        timeout = Objects.requireNonNull(timeout, "timeout");
        budgetLimitUsd = Objects.requireNonNull(budgetLimitUsd, "budgetLimitUsd");
        evaluationSuiteVersion = required(evaluationSuiteVersion, "evaluationSuiteVersion");
        semanticVersion = com.wally.customersupport.agent.domain.model.AgentSemanticVersion
                .parse(semanticVersion).toString();
        invocationConfiguration = Objects.requireNonNull(invocationConfiguration, "invocationConfiguration");
        fallbackAgentId = normalize(fallbackAgentId);

        if (agentVersion < 1) {
            throw new IllegalArgumentException("agentVersion must be positive");
        }
        if (!systemPromptHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("systemPromptHash must be a SHA-256 hexadecimal hash");
        }
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(AgentVersion.MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("timeout must be greater than zero and at most 60 seconds");
        }
        if (maxSteps < 1 || maxSteps > AgentVersion.MAX_STEPS) {
            throw new IllegalArgumentException("maxSteps must be between 1 and " + AgentVersion.MAX_STEPS);
        }
        validateTokenLimit(maxInputTokens, "maxInputTokens");
        validateTokenLimit(maxOutputTokens, "maxOutputTokens");
        if (budgetLimitUsd.signum() < 0) {
            throw new IllegalArgumentException("budgetLimitUsd must not be negative");
        }
    }

    public static AgentRuntimeDefinition from(AgentVersion version) {
        Objects.requireNonNull(version, "version");
        if (!version.canBeActivated()) {
            throw new IllegalArgumentException("agent version is not publishable");
        }
        return new AgentRuntimeDefinition(
                version.agentId(),
                version.version(),
                version.name(),
                version.purpose(),
                version.modelProvider(),
                version.modelId(),
                version.inferenceParameters(),
                version.systemPromptVersion(),
                version.systemPromptHash(),
                version.inputSchemaVersion(),
                version.outputSchemaVersion(),
                version.allowedTools(),
                version.knowledgeSources(),
                version.memoryPolicy(),
                version.responsePolicy(),
                version.timeout(),
                version.maxSteps(),
                version.maxInputTokens(),
                version.maxOutputTokens(),
                version.budgetLimitUsd(),
                version.fallbackAgentId(),
                version.evaluationSuiteVersion(),
                version.semanticVersion(),
                version.invocationConfiguration());
    }

    /** Keep prompt bodies out of accidental log statements. */
    @Override
    public String toString() {
        return "AgentRuntimeDefinition[agentId=" + agentId + ", agentVersion=" + agentVersion
                + ", semanticVersion=" + semanticVersion + ", modelProvider=" + modelProvider
                + ", modelId=" + modelId + ", systemPromptHash=" + systemPromptHash
                + ", invocationConfiguration=<redacted>]";
    }

    private static void validateTokenLimit(int value, String field) {
        if (value < 1 || value > AgentVersion.MAX_TOKENS_PER_DIRECTION) {
            throw new IllegalArgumentException(field + " must be between 1 and "
                    + AgentVersion.MAX_TOKENS_PER_DIRECTION);
        }
    }

    private static Set<String> immutableSet(Set<String> values, String field) {
        Objects.requireNonNull(values, field);
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not contain blank values");
            }
            if (value.length() > 128) {
                throw new IllegalArgumentException(field + " values must be at most 128 characters");
            }
        }
        return Set.copyOf(values);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
