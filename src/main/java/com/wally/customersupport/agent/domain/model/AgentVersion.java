package com.wally.customersupport.agent.domain.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, publishable definition of one agent version.
 *
 * <p>Only prompt metadata is stored here. The prompt content itself is an
 * external artifact and must never be included in operational events or
 * conversation logs.</p>
 */
public record AgentVersion(
        String agentId,
        int version,
        String name,
        String purpose,
        AgentLifecycleState state,
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
        String createdBy,
        Instant createdAt,
        String approvedBy,
        Instant approvedAt) {

    public static final int MAX_STEPS = 3;
    public static final int MAX_TOKENS_PER_DIRECTION = 32_000;
    public static final Duration MAX_TIMEOUT = Duration.ofSeconds(60);

    /**
     * Creates a new immutable definition in the authoring state. Prompt and
     * schema contents remain external artifacts; only their references and
     * hashes belong to the registry definition.
     */
    public static AgentVersion draft(
            String agentId,
            int version,
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
            String createdBy,
            Instant createdAt) {
        return new AgentVersion(
                agentId,
                version,
                name,
                purpose,
                AgentLifecycleState.DRAFT,
                modelProvider,
                modelId,
                inferenceParameters,
                systemPromptVersion,
                systemPromptHash,
                inputSchemaVersion,
                outputSchemaVersion,
                allowedTools,
                knowledgeSources,
                memoryPolicy,
                responsePolicy,
                timeout,
                maxSteps,
                maxInputTokens,
                maxOutputTokens,
                budgetLimitUsd,
                fallbackAgentId,
                evaluationSuiteVersion,
                createdBy,
                createdAt,
                null,
                null);
    }

    public AgentVersion {
        agentId = required(agentId, "agentId");
        name = required(name, "name");
        purpose = required(purpose, "purpose");
        state = Objects.requireNonNull(state, "state");
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
        createdBy = required(createdBy, "createdBy");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        approvedBy = normalize(approvedBy);
        approvedAt = approvedAt;
        fallbackAgentId = normalize(fallbackAgentId);

        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        if (!systemPromptHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("systemPromptHash must be a SHA-256 hexadecimal hash");
        }
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("timeout must be greater than zero and at most 60 seconds");
        }
        if (maxSteps < 1 || maxSteps > MAX_STEPS) {
            throw new IllegalArgumentException("maxSteps must be between 1 and " + MAX_STEPS);
        }
        validateTokenLimit(maxInputTokens, "maxInputTokens");
        validateTokenLimit(maxOutputTokens, "maxOutputTokens");
        if (budgetLimitUsd.signum() < 0) {
            throw new IllegalArgumentException("budgetLimitUsd must not be negative");
        }
        boolean approvedLifecycle = state == AgentLifecycleState.APPROVED
                || state == AgentLifecycleState.ACTIVE
                || state == AgentLifecycleState.DEPRECATED
                || state == AgentLifecycleState.ROLLED_BACK;
        if (approvedLifecycle && (approvedBy == null || approvedAt == null)) {
            throw new IllegalArgumentException("published versions require approval metadata");
        }
        if (!approvedLifecycle && (approvedBy != null || approvedAt != null)) {
            throw new IllegalArgumentException("approval metadata is only valid for evaluated or published versions");
        }
    }

    static AgentVersion withLifecycle(
            AgentVersion source,
            AgentLifecycleState target,
            String actor,
            Instant transitionAt) {
        String normalizedActor = required(actor, "actor");
        Instant normalizedTransitionAt = Objects.requireNonNull(transitionAt, "transitionAt");
        boolean approved = target == AgentLifecycleState.APPROVED
                || target == AgentLifecycleState.ACTIVE
                || target == AgentLifecycleState.DEPRECATED
                || target == AgentLifecycleState.ROLLED_BACK;
        return new AgentVersion(
                source.agentId(),
                source.version(),
                source.name(),
                source.purpose(),
                target,
                source.modelProvider(),
                source.modelId(),
                source.inferenceParameters(),
                source.systemPromptVersion(),
                source.systemPromptHash(),
                source.inputSchemaVersion(),
                source.outputSchemaVersion(),
                source.allowedTools(),
                source.knowledgeSources(),
                source.memoryPolicy(),
                source.responsePolicy(),
                source.timeout(),
                source.maxSteps(),
                source.maxInputTokens(),
                source.maxOutputTokens(),
                source.budgetLimitUsd(),
                source.fallbackAgentId(),
                source.evaluationSuiteVersion(),
                source.createdBy(),
                source.createdAt(),
                target == AgentLifecycleState.APPROVED
                        ? normalizedActor
                        : source.approvedBy(),
                target == AgentLifecycleState.APPROVED
                        ? normalizedTransitionAt
                        : source.approvedAt());
    }

    public boolean canBeActivated() {
        return state == AgentLifecycleState.APPROVED;
    }

    private static void validateTokenLimit(int value, String field) {
        if (value < 1 || value > MAX_TOKENS_PER_DIRECTION) {
            throw new IllegalArgumentException(field + " must be between 1 and " + MAX_TOKENS_PER_DIRECTION);
        }
    }

    private static Set<String> immutableSet(Set<String> values, String field) {
        Set<String> copy = Set.copyOf(Objects.requireNonNull(values, field));
        if (copy.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(field + " must not contain blank values");
        }
        return copy;
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
