package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ConversationRegressionSuiteTest {

    private static final Set<String> DIFFICULTIES = Set.of("easy", "medium", "hard");
    private static final Set<String> WORKFLOWS = Set.of(
            "GREETING", "CATALOG_SEARCH", "PURCHASE_LINK", "BUSINESS_HOURS", "POLICY_QUERY",
            "GENERAL_SUPPORT", "HUMAN_HANDOFF", "ADD_TO_CART", "VIEW_CART", "REMOVE_FROM_CART",
            "CLEAR_CART", "REVIEW_CHECKOUT", "CONFIRM_CHECKOUT", "CANCEL_CHECKOUT", "OPT_OUT", "UNKNOWN");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void suiteHasBalancedDifficultyAndSanitizedRunnableScenarios() throws IOException {
        JsonNode scenarios = scenarios();
        assertTrue(scenarios.size() >= 50, "the regression suite must stay broad");

        Set<String> ids = new HashSet<>();
        Set<String> difficulties = new HashSet<>();
        for (JsonNode scenario : scenarios) {
            String id = scenario.path("id").asText();
            String difficulty = scenario.path("difficulty").asText();
            assertTrue(ids.add(id), "duplicate scenario id: " + id);
            assertTrue(DIFFICULTIES.contains(difficulty), "unsupported difficulty: " + difficulty);
            difficulties.add(difficulty);
            assertTrue(scenario.path("messages").isArray());
            assertTrue(scenario.path("messages").size() >= 1);
            assertTrue(scenario.path("messages").size() <= 6);
            assertTrue(WORKFLOWS.contains(scenario.path("expectedWorkflow").asText()));
            assertFalse(scenario.toString().matches(".*(token|password|Bearer|@gmail|@hotmail).*"),
                    "fixture must not contain secrets or personal data: " + id);

            JsonNode route = scenario.path("route");
            if (route.isNull() || route.isMissingNode()) {
                assertEquals("OPT_OUT", scenario.path("expectedWorkflow").asText(),
                        "non-routable scenarios must be explicit pre-router opt-out cases");
            } else {
                assertTrue(route.path("intent").isTextual(), id + " has no route intent");
                assertTrue(route.path("action").isTextual(), id + " has no route action");
                assertTrue(route.path("confidence").isNumber(), id + " has no confidence");
                assertTrue(route.path("missingParameters").isArray(), id + " has no missingParameters");
            }
        }

        assertEquals(Set.of("easy", "medium", "hard"), difficulties);
        assertTrue(count(scenarios, "easy") >= 17);
        assertTrue(count(scenarios, "medium") >= 17);
        assertTrue(count(scenarios, "hard") >= 17);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routableScenarios")
    void structuredRouteContractParsesEveryRegressionScenario(String id, JsonNode scenario) throws Exception {
        JsonNode route = scenario.path("route");
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        when(converseClient.completeForRouter(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(), anyString(), anyString()))
                .thenReturn(MAPPER.writeValueAsString(route));

        ConversationIntentDecision decision = new BedrockConversationIntentClassifier(
                converseClient, MAPPER).classify(contextFor(scenario));

        assertEquals(route.path("intent").asText(), decision.intent().name(), id);
        assertEquals(route.path("action").asText(), decision.action().name(), id);
        assertEquals(route.path("confidence").asDouble(), decision.confidence(), 0.0001, id);
        assertEquals(route.path("quantity").asInt(1), decision.quantity(), id);
        assertEquals(route.path("policyKey").isNull() ? null : route.path("policyKey").asText(), decision.policyKey(), id);
        assertEquals(route.path("missingParameters").size(), decision.missingParameters().size(), id);
        for (int index = 0; index < route.path("missingParameters").size(); index++) {
            assertEquals(route.path("missingParameters").get(index).asText(), decision.missingParameters().get(index), id);
        }
        assertQueryEquals(route.path("catalogQuery"), decision.catalogQuery(), id);
    }

    @Test
    void suiteCoversTheHighRiskContrastiveBoundaries() throws IOException {
        JsonNode scenarios = scenarios();
        assertEquals("CATALOG_SEARCH", scenario(scenarios, "M-002").path("route").path("intent").asText());
        assertEquals("PURCHASE_LINK", scenario(scenarios, "E-011").path("route").path("intent").asText());
        assertEquals("ADD_TO_CART", scenario(scenarios, "M-010").path("route").path("action").asText());
        assertEquals("CANCEL_CHECKOUT", scenario(scenarios, "M-014").path("route").path("action").asText());
        assertEquals("OPT_OUT", scenario(scenarios, "M-017").path("expectedWorkflow").asText());
        assertEquals("UNKNOWN", scenario(scenarios, "H-016").path("route").path("intent").asText());
    }

    static Stream<Arguments> routableScenarios() throws IOException {
        List<Arguments> result = new ArrayList<>();
        for (JsonNode scenario : scenarios()) {
            if (scenario.path("route").isObject()) {
                result.add(Arguments.of(scenario.path("id").asText(), scenario));
            }
        }
        return result.stream();
    }

    private static JsonNode scenarios() throws IOException {
        try (InputStream input = ConversationRegressionSuiteTest.class
                .getResourceAsStream("/fixtures/conversation-regression-v1.json")) {
            assertNotNull(input);
            JsonNode root = MAPPER.readTree(input);
            assertEquals("conversation-regression-v1", root.path("datasetVersion").asText());
            return root.path("scenarios");
        }
    }

    private static JsonNode scenario(JsonNode scenarios, String id) {
        for (JsonNode scenario : scenarios) {
            if (id.equals(scenario.path("id").asText())) {
                return scenario;
            }
        }
        throw new AssertionError("missing scenario: " + id);
    }

    private static long count(JsonNode scenarios, String difficulty) {
        long count = 0;
        for (JsonNode scenario : scenarios) {
            if (difficulty.equals(scenario.path("difficulty").asText())) {
                count++;
            }
        }
        return count;
    }

    private static ConversationContext contextFor(JsonNode scenario) {
        List<String> messages = new ArrayList<>();
        for (JsonNode message : scenario.path("messages")) {
            messages.add(message.asText());
        }
        String latest = messages.getLast();
        List<String> recentMessages = new ArrayList<>(messages.subList(0, messages.size() - 1));
        Collections.reverse(recentMessages);
        recentMessages.add(0, latest);
        return new ConversationContext(null, "synthetic-customer", latest, recentMessages, List.of());
    }

    private static void assertQueryEquals(JsonNode expected, CatalogQuery actual, String id) {
        if (expected == null || expected.isNull()) {
            assertNull(actual, id);
            return;
        }
        assertNotNull(actual, id);
        assertEquals(textOrNull(expected, "name"), actual.name(), id);
        assertEquals(textOrNull(expected, "sku"), actual.sku(), id);
        assertEquals(textOrNull(expected, "size"), actual.size(), id);
        assertEquals(textOrNull(expected, "color"), actual.color(), id);
        assertEquals(textOrNull(expected, "productType"), actual.productType(), id);
        assertEquals(decimalOrNull(expected, "minPrice"), actual.minPrice(), id);
        assertEquals(decimalOrNull(expected, "maxPrice"), actual.maxPrice(), id);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : new BigDecimal(value.asText());
    }
}
