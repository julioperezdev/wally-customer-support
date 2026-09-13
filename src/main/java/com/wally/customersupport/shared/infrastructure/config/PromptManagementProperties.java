package com.wally.customersupport.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** References to immutable prompts managed by Amazon Bedrock Prompt Management. */
@ConfigurationProperties(prefix = "wcs.ai.prompt.management")
public record PromptManagementProperties(
        String intentIdentifier,
        String intentVersion,
        String responseIdentifier,
        String responseVersion) {

    public String effectiveIntentIdentifier() {
        return required(
                intentIdentifier,
                "wcs.ai.prompt.management.intent-identifier",
                "[A-Za-z0-9:/_.-]{1,256}");
    }

    public String effectiveIntentVersion() {
        return required(
                intentVersion,
                "wcs.ai.prompt.management.intent-version",
                "[1-9][0-9]{0,8}");
    }

    public String effectiveResponseIdentifier() {
        return required(
                responseIdentifier,
                "wcs.ai.prompt.management.response-identifier",
                "[A-Za-z0-9:/_.-]{1,256}");
    }

    public String effectiveResponseVersion() {
        return required(
                responseVersion,
                "wcs.ai.prompt.management.response-version",
                "[1-9][0-9]{0,8}");
    }

    private static String required(String value, String property, String pattern) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || !normalized.matches(pattern)) {
            throw new IllegalStateException(property + " must be a valid immutable Bedrock prompt reference");
        }
        return normalized;
    }
}
