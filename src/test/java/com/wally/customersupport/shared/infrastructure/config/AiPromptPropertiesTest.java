package com.wally.customersupport.shared.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class AiPromptPropertiesTest {

    @Test
    void appliesSafeDefaultsWhenRuntimeValuesAreMissingOrOutOfBounds() {
        AiPromptProperties properties = new AiPromptProperties(
                " ",
                9_999,
                new BigDecimal("2.5"),
                0,
                99);

        assertEquals("conversation-intent-v2", properties.effectiveIntentVersion());
        assertEquals(1_024, properties.effectiveIntentMaxOutputTokens());
        assertEquals(0.0f, properties.effectiveIntentTemperature());
        assertEquals(2_000, properties.effectiveMaxInputCharacters());
        assertEquals(12, properties.effectiveMaxHistoryMessages());
    }

    @Test
    void acceptsValuesInsideTheApprovedInferenceBounds() {
        AiPromptProperties properties = new AiPromptProperties(
                "conversation-intent-v2",
                512,
                new BigDecimal("0.2"),
                1_500,
                8);

        assertEquals("conversation-intent-v2", properties.effectiveIntentVersion());
        assertEquals(512, properties.effectiveIntentMaxOutputTokens());
        assertEquals(0.2f, properties.effectiveIntentTemperature());
        assertEquals(1_500, properties.effectiveMaxInputCharacters());
        assertEquals(8, properties.effectiveMaxHistoryMessages());
    }
}
