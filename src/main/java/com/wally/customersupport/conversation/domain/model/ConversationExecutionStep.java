package com.wally.customersupport.conversation.domain.model;

import java.util.Objects;

public record ConversationExecutionStep(
        String stepId,
        String owner,
        String capability) {

    public ConversationExecutionStep {
        stepId = required(stepId, "stepId");
        owner = required(owner, "owner");
        capability = required(capability, "capability");
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
