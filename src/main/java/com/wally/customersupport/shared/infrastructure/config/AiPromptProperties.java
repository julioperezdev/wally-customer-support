package com.wally.customersupport.shared.infrastructure.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime selection and bounded inference settings for approved WCS prompts. */
@ConfigurationProperties(prefix = "wcs.ai.prompt")
public record AiPromptProperties(
        String intentVersion,
        Integer intentMaxOutputTokens,
        BigDecimal intentTemperature,
        Integer maxInputCharacters,
        Integer maxHistoryMessages) {

    public String effectiveIntentVersion() {
        return nonBlank(intentVersion, "conversation-intent-v1");
    }

    public int effectiveIntentMaxOutputTokens() {
        return bounded(intentMaxOutputTokens, 1, 1_024, 1_024);
    }

    public float effectiveIntentTemperature() {
        BigDecimal value = intentTemperature == null ? BigDecimal.ZERO : intentTemperature;
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(2)) > 0) {
            return 0.0f;
        }
        return value.floatValue();
    }

    public int effectiveMaxInputCharacters() {
        return bounded(maxInputCharacters, 1, 2_000, 2_000);
    }

    public int effectiveMaxHistoryMessages() {
        return bounded(maxHistoryMessages, 1, 20, 12);
    }

    private static int bounded(Integer value, int minimum, int maximum, int fallback) {
        if (value == null || value < minimum || value > maximum) {
            return fallback;
        }
        return value;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
