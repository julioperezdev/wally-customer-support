package com.wally.customersupport.shared.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class AiResponsePropertiesTest {

    @Test
    void appliesSafeDefaultsAndBounds() {
        AiResponseProperties invalid = new AiResponseProperties(
                " ", 2_000, new BigDecimal("3"), 0, 30, 20_000, 0);

        assertEquals("conversation-response-v1", invalid.effectivePromptVersion());
        assertEquals(1_024, invalid.effectiveMaxOutputTokens());
        assertEquals(0.2f, invalid.effectiveTemperature());
        assertEquals(2_000, invalid.effectiveMaxInputCharacters());
        assertEquals(6, invalid.effectiveMaxHistoryMessages());
        assertEquals(8_000, invalid.effectiveMaxKnowledgeCharacters());
        assertEquals(4_000, invalid.effectiveMaxSummaryCharacters());
    }
}
