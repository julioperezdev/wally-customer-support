package com.wally.customersupport.shared.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PromptManagementPropertiesTest {

    @Test
    void acceptsPromptIdentifiersAndImmutableNumericVersions() {
        PromptManagementProperties properties = new PromptManagementProperties(
                "arn:aws:bedrock:us-east-1:123456789012:preset/intent",
                "12",
                "response-prompt",
                "3");

        assertEquals("arn:aws:bedrock:us-east-1:123456789012:preset/intent", properties.effectiveIntentIdentifier());
        assertEquals("12", properties.effectiveIntentVersion());
        assertEquals("response-prompt", properties.effectiveResponseIdentifier());
        assertEquals("3", properties.effectiveResponseVersion());
    }

    @Test
    void rejectsMissingOrMutablePromptReferences() {
        PromptManagementProperties properties = new PromptManagementProperties(
                " ", "DRAFT", "response prompt", "0");

        assertThrows(IllegalStateException.class, properties::effectiveIntentIdentifier);
        assertThrows(IllegalStateException.class, properties::effectiveIntentVersion);
        assertThrows(IllegalStateException.class, properties::effectiveResponseIdentifier);
        assertThrows(IllegalStateException.class, properties::effectiveResponseVersion);
    }
}
