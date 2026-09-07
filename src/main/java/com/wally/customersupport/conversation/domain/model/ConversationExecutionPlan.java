package com.wally.customersupport.conversation.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ConversationExecutionPlan(
        String workflowVersion,
        String useCase,
        ConversationExecutionAction action,
        List<ConversationExecutionStep> steps,
        int maxSteps,
        boolean fallbackAllowed,
        String fallbackReason) {

    public static final String CURRENT_WORKFLOW_VERSION = "wcs-agent-runtime-v1";
    private static final int ABSOLUTE_MAX_STEPS = 3;
    private static final Set<String> ALLOWED_OWNERS = Set.of(
            "catalog-specialist",
            "knowledge-specialist",
            "support-safety",
            "response-humanizer");
    private static final Set<String> ALLOWED_CAPABILITIES = Set.of(
            "direct-response",
            "catalog-query",
            "business-hours",
            "policy-query",
            "knowledge-retrieval",
            "response-generation",
            "human-handoff",
            "safe-fallback");

    public ConversationExecutionPlan {
        workflowVersion = required(workflowVersion, "workflowVersion");
        useCase = required(useCase, "useCase");
        action = Objects.requireNonNull(action, "action");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        fallbackReason = normalize(fallbackReason);

        if (steps.isEmpty()) {
            throw new IllegalArgumentException("steps must not be empty");
        }
        if (maxSteps < 1 || maxSteps > ABSOLUTE_MAX_STEPS) {
            throw new IllegalArgumentException("maxSteps must be between 1 and " + ABSOLUTE_MAX_STEPS);
        }
        if (steps.size() > maxSteps) {
            throw new IllegalArgumentException("steps exceed maxSteps");
        }
        steps.forEach(ConversationExecutionPlan::validateStep);
        if (!fallbackAllowed && fallbackReason != null) {
            throw new IllegalArgumentException("fallbackReason requires fallbackAllowed");
        }
    }

    public int stepCount() {
        return steps.size();
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

    private static void validateStep(ConversationExecutionStep step) {
        if (!ALLOWED_OWNERS.contains(step.owner())) {
            throw new IllegalArgumentException("Unsupported execution owner: " + step.owner());
        }
        if (!ALLOWED_CAPABILITIES.contains(step.capability())) {
            throw new IllegalArgumentException("Unsupported execution capability: " + step.capability());
        }
    }
}
