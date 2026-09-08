package com.wally.customersupport.shared.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class AiPropertiesTest {

    @Test
    void boundsBedrockRequestTimeoutToARecoverableWindow() {
        AiProperties invalid = new AiProperties(
                "bedrock", "model", "us-east-1", "pricing", BigDecimal.ZERO, BigDecimal.ZERO,
                Duration.ofSeconds(90));
        AiProperties configured = new AiProperties(
                "bedrock", "model", "us-east-1", "pricing", BigDecimal.ZERO, BigDecimal.ZERO,
                Duration.ofSeconds(12));

        assertEquals(Duration.ofSeconds(30), invalid.effectiveRequestTimeout());
        assertEquals(Duration.ofSeconds(12), configured.effectiveRequestTimeout());
    }
}
