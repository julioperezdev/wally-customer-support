package com.wally.customersupport.conversation.infrastructure.ai.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ConversationIntentV4FixtureTest {

    private static final Set<String> INTENTS = Set.of(
            "GREETING", "CATALOG_SEARCH", "PURCHASE_LINK", "BUSINESS_HOURS", "POLICY_QUERY",
            "HUMAN_HANDOFF", "GENERAL_SUPPORT", "UNKNOWN");
    private static final Set<String> ACTIONS = Set.of(
            "GREETING", "CATALOG_SEARCH", "ADD_TO_CART", "VIEW_CART", "REMOVE_FROM_CART",
            "CLEAR_CART", "REVIEW_CHECKOUT", "CONFIRM_CHECKOUT", "CANCEL_CHECKOUT", "PURCHASE_LINK", "BUSINESS_HOURS",
            "POLICY_QUERY", "HUMAN_HANDOFF", "GENERAL_SUPPORT", "UNKNOWN");

    @Test
    void fixtureContainsSanitizedContrastiveScenariosWithAllowListedOutputs() throws IOException {
        JsonNode scenarios;
        try (InputStream input = getClass().getResourceAsStream("/fixtures/conversation-intent-v4.json")) {
            assertNotNull(input);
            scenarios = new ObjectMapper().readTree(input);
        }

        assertTrue(scenarios.isArray());
        assertTrue(scenarios.size() >= 15);
        assertEquals("CATALOG_SEARCH", scenario(scenarios, "category_interest_is_not_purchase")
                .path("expectedIntent").asText());
        assertEquals("PURCHASE_LINK", scenario(scenarios, "explicit_single_variant_purchase")
                .path("expectedIntent").asText());
        assertEquals("M", scenario(scenarios, "size_only_is_not_product_name")
                .path("expectedSize").asText());
        assertEquals("remera", scenario(scenarios, "category_after_size_keeps_context")
                .path("expectedProductType").asText());
        assertEquals("CATALOG_SEARCH", scenario(scenarios, "generic_ropa_question_clears_previous_filters")
                .path("expectedAction").asText());

        for (JsonNode scenario : scenarios) {
            assertFalse(scenario.path("name").asText().isBlank());
            assertFalse(scenario.path("message").asText().isBlank());
            assertTrue(INTENTS.contains(scenario.path("expectedIntent").asText()));
            assertTrue(ACTIONS.contains(scenario.path("expectedAction").asText()));
        }
    }

    @Test
    void packagedPromptV4ContainsTheCriticalContrastiveGuidance() {
        PromptDefinition definition = new ClasspathPromptRegistry()
                .intentPrompt("conversation-intent-v4");

        assertEquals("conversation-intent-v4", definition.version());
        assertTrue(definition.content().contains("Quiero un buzo"));
        assertTrue(definition.content().contains("Quiero comprar el buzo negro talle XL"));
        assertTrue(definition.content().contains("Soy talle M"));
        assertTrue(definition.content().contains("Tenes ropa"));
        assertTrue(definition.content().contains("interes no es compra"));
        assertEquals(64, definition.sha256().length());
    }

    @Test
    void packagedKnowledgePromptIsAvailableForThePublishedAgentDefinition() {
        PromptDefinition definition = new ClasspathPromptRegistry()
                .responsePrompt("knowledge-system-v2");

        assertEquals("knowledge-system-v2", definition.version());
        assertEquals(
                "f257258bb932f92b0b22657b64590d8ac7f79eb0999855e502072388b389447f",
                definition.sha256());
        assertTrue(definition.content().contains("approved_knowledge"));
        assertTrue(definition.content().contains("No inventes"));
    }

    private JsonNode scenario(JsonNode scenarios, String name) {
        for (JsonNode scenario : scenarios) {
            if (name.equals(scenario.path("name").asText())) {
                return scenario;
            }
        }
        throw new AssertionError("Missing scenario: " + name);
    }
}
