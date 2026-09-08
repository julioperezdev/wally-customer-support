package com.wally.customersupport.conversation.infrastructure.ai.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClasspathPromptRegistryTest {

    private final ClasspathPromptRegistry registry = new ClasspathPromptRegistry();

    @Test
    void loadsApprovedPromptWithStableVersionAndHash() {
        PromptDefinition definition = registry.intentPrompt("conversation-intent-v1");

        assertEquals("conversation-intent", definition.id());
        assertEquals("conversation-intent-v1", definition.version());
        assertTrue(definition.content().contains("Intenciones permitidas"));
        assertEquals(64, definition.sha256().length());
    }

    @Test
    void rejectsPathTraversalAndUnknownVersions() {
        assertThrows(IllegalArgumentException.class, () -> registry.intentPrompt("../secret"));
        assertThrows(IllegalStateException.class, () -> registry.intentPrompt("conversation-intent-v99"));
    }
}
