package com.wally.customersupport.conversation.domain.model;

import java.util.Objects;

public record ConversationExecutionResult(
        String workflowVersion,
        String useCase,
        String outcome,
        String response,
        String fallbackReason,
        int stepCount,
        String mediaReference,
        ConversationWorkingMemory workingMemory) {

    public ConversationExecutionResult {
        workflowVersion = required(workflowVersion, "workflowVersion");
        useCase = required(useCase, "useCase");
        outcome = required(outcome, "outcome");
        response = Objects.requireNonNullElse(response, "");
        fallbackReason = normalize(fallbackReason);
        if (stepCount < 1) {
            throw new IllegalArgumentException("stepCount must be positive");
        }
        mediaReference = normalize(mediaReference);
        workingMemory = workingMemory == null ? ConversationWorkingMemory.empty() : workingMemory;
    }

    public ConversationExecutionResult(
            String workflowVersion,
            String useCase,
            String outcome,
            String response,
            String fallbackReason,
            int stepCount) {
        this(workflowVersion, useCase, outcome, response, fallbackReason, stepCount, null,
                ConversationWorkingMemory.empty());
    }

    public ConversationExecutionResult(
            String workflowVersion,
            String useCase,
            String outcome,
            String response,
            String fallbackReason,
            int stepCount,
            String mediaReference) {
        this(workflowVersion, useCase, outcome, response, fallbackReason, stepCount, mediaReference,
                ConversationWorkingMemory.empty());
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
                plan.stepCount(),
                null,
                ConversationWorkingMemory.empty());
    }

    public static ConversationExecutionResult completed(
            ConversationExecutionPlan plan,
            String response,
            String mediaReference) {
        return completed(plan, response, mediaReference, ConversationWorkingMemory.empty());
    }

    public static ConversationExecutionResult completed(
            ConversationExecutionPlan plan,
            String response,
            String mediaReference,
            ConversationWorkingMemory workingMemory) {
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
                plan.stepCount(),
                mediaReference,
                workingMemory);
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
                plan.stepCount(),
                null,
                ConversationWorkingMemory.empty());
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
