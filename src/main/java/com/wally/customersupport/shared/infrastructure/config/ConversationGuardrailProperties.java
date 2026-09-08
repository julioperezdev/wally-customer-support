package com.wally.customersupport.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Deterministic application boundaries applied after model output is parsed. */
@ConfigurationProperties(prefix = "wcs.conversation.guardrails")
public record ConversationGuardrailProperties(Double minIntentConfidence) {

    public double effectiveMinIntentConfidence() {
        if (minIntentConfidence == null || !Double.isFinite(minIntentConfidence)
                || minIntentConfidence < 0.0 || minIntentConfidence > 1.0) {
            return 0.65;
        }
        return minIntentConfidence;
    }
}
