package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.application.tool.ConversationRouteToolContract;
import com.wally.customersupport.shared.infrastructure.config.AiProperties;
import com.wally.customersupport.conversation.application.port.out.MeasuredLlmClient;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class BedrockConversationIntentClassifierTest {

    @Test
    void parsesStructuredCatalogDecisionWithoutAllowingModelDataToBecomeSql() {
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        when(converseClient.completeForRouter(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(), anyString(), anyString()))
                .thenReturn("""
                        {"intent":"CATALOG_SEARCH","confidence":0.94,
                         "catalogQuery":{"name":"camiseta","sku":null,"size":"M","color":"negro",
                         "minPrice":null,"maxPrice":20000},
                         "policyKey":null}
                        """);

        var decision = new BedrockConversationIntentClassifier(converseClient, new ObjectMapper())
                .classify("¿Tienen camisetas oscuras en mediano?");

        assertEquals(ConversationIntent.CATALOG_SEARCH, decision.intent());
        assertEquals(0.94, decision.confidence());
        assertEquals("camiseta", decision.catalogQuery().name());
        assertEquals("M", decision.catalogQuery().size());
        assertEquals("negro", decision.catalogQuery().color());
        assertEquals(new BigDecimal("20000"), decision.catalogQuery().maxPrice());
        verify(converseClient).completeForRouter(
                anyString(), anyString(), anyString(), anyString(), eq(2_048), eq(0.0f),
                eq("conversation-intent-v4"), anyString());
    }

    @Test
    void sendsBoundedConversationHistoryToResolveRefinements() {
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        when(converseClient.completeForRouter(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(), anyString(), anyString()))
                .thenReturn("{\"intent\":\"CATALOG_SEARCH\",\"confidence\":0.94,"
                        + "\"catalogQuery\":{\"name\":\"nullpointer\",\"sku\":null,\"size\":null,"
                        + "\"color\":\"negro\",\"productType\":\"buzo\"},\"policyKey\":null}");

        var classifier = new BedrockConversationIntentClassifier(converseClient, new ObjectMapper());
        var decision = classifier.classify(new ConversationContext(
                null, "customer-1", "que sea negro", List.of("que sea negro", "quiero un buzo"), List.of()));

        assertEquals("buzo", decision.catalogQuery().productType());
        org.mockito.ArgumentCaptor<String> prompt = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(converseClient).completeForRouter(
                anyString(), anyString(), anyString(), prompt.capture(), eq(2_048), eq(0.0f),
                eq("conversation-intent-v4"), anyString());
        assertTrue(prompt.getValue().contains("quiero un buzo"));
        assertTrue(prompt.getValue().contains("que sea negro"));
    }

    @Test
    void convertsMalformedModelOutputToUnknownDecision() {
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        when(converseClient.completeForRouter(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(), anyString(), anyString()))
                .thenReturn("not-json");

        var decision = new BedrockConversationIntentClassifier(converseClient, new ObjectMapper())
                .classify("consulta");

        assertEquals(ConversationIntent.UNKNOWN, decision.intent());
        assertEquals(0.0, decision.confidence());
    }

    @Test
    void usesBoundedConfidenceWhenGeneralSupportOmitsConfidence() {
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        when(converseClient.completeForRouter(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(), anyString(), anyString()))
                .thenReturn("{\"intent\":\"GENERAL_SUPPORT\",\"catalogQuery\":null,\"policyKey\":null}");

        var decision = new BedrockConversationIntentClassifier(converseClient, new ObjectMapper())
                .classify("¿Dónde están ubicados?");

        assertEquals(ConversationIntent.GENERAL_SUPPORT, decision.intent());
        assertEquals(0.70, decision.confidence());
    }

    @Test
    void usesSafeDefaultConfidenceWhenHumanHandoffOmitsConfidence() {
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        when(converseClient.completeForRouter(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(), anyString(), anyString()))
                .thenReturn("{\"intent\":\"HUMAN_HANDOFF\",\"action\":\"HUMAN_HANDOFF\","
                        + "\"confidence\":0.0}");

        var decision = new BedrockConversationIntentClassifier(converseClient, new ObjectMapper())
                .classify("Necesito hablar con una persona");

        assertEquals(ConversationIntent.HUMAN_HANDOFF, decision.intent());
        assertEquals(ConversationAction.HUMAN_HANDOFF, decision.action());
        assertEquals(0.90, decision.confidence());
    }

    @Test
    void usesSafeDefaultConfidenceWhenPolicyQueryOmitsConfidence() {
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        when(converseClient.completeForRouter(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(), anyString(), anyString()))
                .thenReturn("{\"intent\":\"POLICY_QUERY\",\"action\":\"POLICY_QUERY\","
                        + "\"confidence\":0.0,\"policyKey\":\"shipping\"}");

        var decision = new BedrockConversationIntentClassifier(converseClient, new ObjectMapper())
                .classify("¿Cómo funcionan los envíos?");

        assertEquals(ConversationIntent.POLICY_QUERY, decision.intent());
        assertEquals(ConversationAction.POLICY_QUERY, decision.action());
        assertEquals(0.90, decision.confidence());
        assertEquals("shipping", decision.policyKey());
    }

    @Test
    void parsesPurchaseIntentAndCatalogSelection() {
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        when(converseClient.completeForRouter(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(), anyString(), anyString()))
                .thenReturn("{\"intent\":\"PURCHASE_LINK\",\"confidence\":0.98,"
                        + "\"catalogQuery\":{\"name\":\"nullpointer\",\"sku\":null,\"size\":\"M\","
                        + "\"color\":\"negro\",\"productType\":\"remera\",\"minPrice\":null,\"maxPrice\":null},"
                        + "\"policyKey\":null}");

        var decision = new BedrockConversationIntentClassifier(converseClient, new ObjectMapper())
                .classify("Quiero comprar la remera NullPointer negra talle M");

        assertEquals(ConversationIntent.PURCHASE_LINK, decision.intent());
        assertEquals("nullpointer", decision.catalogQuery().name());
        assertEquals("M", decision.catalogQuery().size());
    }

    @Test
    void parsesStructuredCartActionAndBoundedQuantity() {
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        when(converseClient.completeForRouter(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(), anyString(), anyString()))
                .thenReturn("""
                        {"intent":"CATALOG_SEARCH","action":"ADD_TO_CART","confidence":0.96,
                         "quantity":2,"missingParameters":[],
                         "catalogQuery":{"name":"spring boot","sku":null,"size":"XL",
                         "color":"negro","productType":"buzo","minPrice":null,"maxPrice":null},
                         "policyKey":null}
                        """);

        var decision = new BedrockConversationIntentClassifier(converseClient, new ObjectMapper())
                .classify("Sumame dos de esos, el negro talle XL");

        assertEquals(ConversationAction.ADD_TO_CART, decision.action());
        assertEquals(2, decision.quantity());
        assertTrue(decision.missingParameters().isEmpty());
        assertEquals("spring boot", decision.catalogQuery().name());
    }

    @Test
    void derivesLegacyActionWhenManagedPromptDoesNotReturnAction() {
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        when(converseClient.completeForRouter(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(), anyString(), anyString()))
                .thenReturn("{" +
                        "\"intent\":\"CATALOG_SEARCH\",\"confidence\":0.90," +
                        "\"catalogQuery\":{\"name\":\"buzo\"},\"policyKey\":null}");

        var decision = new BedrockConversationIntentClassifier(converseClient, new ObjectMapper())
                .classify("¿Qué buzos tienen?");

        assertEquals(ConversationAction.CATALOG_SEARCH, decision.action());
    }

    @Test
    void usesBedrockToolUseWhenStructuredRoutingIsEnabled() {
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        when(converseClient.completeWithToolUseForRouter(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat(), anyString(), anyString(),
                any(com.wally.customersupport.conversation.application.tool.WcsToolDescriptor.class)))
                .thenReturn(new BedrockConverseClient.ToolUseCompletion(
                        ConversationRouteToolContract.NAME,
                        "{\"intent\":\"CATALOG_SEARCH\",\"action\":\"CATALOG_SEARCH\","
                                + "\"confidence\":0.97,\"quantity\":1,\"catalogQuery\":{"
                                + "\"name\":\"buzo\",\"sku\":null,\"size\":null,\"color\":null,"
                                + "\"productType\":\"buzo\",\"minPrice\":null,\"maxPrice\":null},"
                                + "\"policyKey\":null,\"missingParameters\":[]}",
                        new MeasuredLlmClient.LlmCompletion(
                                null, "bedrock", "model-v1", 10, 8L, 20, 15, 35,
                                new BigDecimal("0.0001"), "pricing-v1")));

        AiProperties properties = new AiProperties(
                "bedrock", "model-v1", "us-east-1", "pricing-v1", BigDecimal.ZERO, BigDecimal.ZERO,
                java.time.Duration.ofSeconds(30), true);
        var classifier = new BedrockConversationIntentClassifier(
                converseClient,
                new ObjectMapper(),
                new com.wally.customersupport.shared.infrastructure.config.AiPromptProperties(
                        "conversation-intent-v4", 2048, BigDecimal.ZERO, 2000, 12),
                new com.wally.customersupport.conversation.infrastructure.ai.prompt.ClasspathPromptRegistry(),
                properties);

        var decision = classifier.classify("Quiero un buzo");

        assertEquals(ConversationIntent.CATALOG_SEARCH, decision.intent());
        assertEquals(ConversationAction.CATALOG_SEARCH, decision.action());
        assertEquals("buzo", decision.catalogQuery().productType());
        verify(converseClient).completeWithToolUseForRouter(
                anyString(), anyString(), anyString(), anyString(), eq(2_048), eq(0.0f),
                eq("conversation-intent-v4"), anyString(), eq(ConversationRouteToolContract.DESCRIPTOR));
    }
}
