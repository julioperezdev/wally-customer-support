package com.wally.customersupport.conversation.infrastructure.ai.prompt;

import java.util.Objects;

/** Approved prompt metadata; prompt content never belongs in logs or usage metadata. */
public record PromptDefinition(
        String id,
        String version,
        String content,
        String sha256) {

    public PromptDefinition {
        id = required(id, "id");
        version = required(version, "version");
        content = required(content, "content");
        sha256 = required(sha256, "sha256");
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
