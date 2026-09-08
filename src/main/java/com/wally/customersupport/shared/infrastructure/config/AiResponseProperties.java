package com.wally.customersupport.shared.infrastructure.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bounded runtime settings for the customer-facing response prompt. */
@ConfigurationProperties(prefix = "wcs.ai.response")
public record AiResponseProperties(
        String promptVersion,
        Integer maxOutputTokens,
        BigDecimal temperature,
        Integer maxInputCharacters,
        Integer maxHistoryMessages,
        Integer maxKnowledgeCharacters,
        Integer maxSummaryCharacters) {

    public String effectivePromptVersion() {
        return promptVersion == null || promptVersion.isBlank()
                ? "conversation-response-v1"
                : promptVersion.trim();
    }

    public int effectiveMaxOutputTokens() {
        return bounded(maxOutputTokens, 1, 1_024, 1_024);
    }

    public float effectiveTemperature() {
        BigDecimal value = temperature == null ? BigDecimal.valueOf(0.2) : temperature;
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(2)) > 0) {
            return 0.2f;
        }
        return value.floatValue();
    }

    public int effectiveMaxInputCharacters() {
        return bounded(maxInputCharacters, 1, 2_000, 2_000);
    }

    public int effectiveMaxHistoryMessages() {
        return bounded(maxHistoryMessages, 1, 20, 6);
    }

    public int effectiveMaxKnowledgeCharacters() {
        return bounded(maxKnowledgeCharacters, 1, 12_000, 8_000);
    }

    public int effectiveMaxSummaryCharacters() {
        return bounded(maxSummaryCharacters, 1, 8_000, 4_000);
    }

    private static int bounded(Integer value, int minimum, int maximum, int fallback) {
        if (value == null || value < minimum || value > maximum) {
            return fallback;
        }
        return value;
    }
}
