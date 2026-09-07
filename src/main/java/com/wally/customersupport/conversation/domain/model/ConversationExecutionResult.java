package com.wally.customersupport.conversation.domain.model;

import java.util.Objects;

public record ConversationExecutionResult(
        String workflowVersion,
        String useCase,
        String outcome,
        String response,
        String fallbackReason,
        int stepCount) {

    public ConversationExecutionResult {
        workflowVersion = required(workflowVersion, "workflowVersion");
        useCase = required(useCase, "useCase");
        outcome = required(outcome, "outcome");
        response = Objects.requireNonNullElse(response, "");
        fallbackReason = normalize(fallbackReason);
        if (stepCount < 1) {
            throw new IllegalArgumentException("stepCount must be positive");
        }
    }

    public static ConversationExecutionResult completed(
            ConversationExecutionPlan plan,
            String response) {
        String outcome = switch (plan.action()) {
            case LOW_CONFIDENCE -> "LOW_CONFIDENCE";
            case SAFE_FALLBACK -> "FALLBACK";
            case HUMAN_HANDOFF -> "HANDOFF";
            default -> "REPLIED";
        };
        return new ConversationExecutionResult(
                plan.workflowVersion(),
                plan.useCase(),
                outcome,
                response,
                plan.fallbackReason(),
                plan.stepCount());
    }

    public static ConversationExecutionResult fallback(
            ConversationExecutionPlan plan,
            String response,
            String reason) {
        return new ConversationExecutionResult(
                plan.workflowVersion(),
                plan.useCase(),
                "FALLBACK",
                response,
                reason,
                plan.stepCount());
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
