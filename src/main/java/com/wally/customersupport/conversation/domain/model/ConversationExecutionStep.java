package com.wally.customersupport.conversation.domain.model;

import java.util.Objects;

public record ConversationExecutionStep(
        String stepId,
        String owner,
        String capability,
        String toolName,
        String inputSchemaVersion,
        String outputSchemaVersion) {

    public ConversationExecutionStep(String stepId, String owner, String capability) {
        this(stepId, owner, capability, null, null, null);
    }

    public ConversationExecutionStep {
        stepId = required(stepId, "stepId");
        owner = required(owner, "owner");
        capability = required(capability, "capability");
        toolName = normalize(toolName);
        inputSchemaVersion = normalize(inputSchemaVersion);
        outputSchemaVersion = normalize(outputSchemaVersion);
        if (toolName == null && (inputSchemaVersion != null || outputSchemaVersion != null)) {
            throw new IllegalArgumentException("schema versions require a toolName");
        }
        if (toolName != null && (inputSchemaVersion == null || outputSchemaVersion == null)) {
            throw new IllegalArgumentException("toolName requires input and output schema versions");
        }
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
