package com.wally.customersupport.adapter.out.ai.bedrock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wally.customersupport.domain.model.ConversationIntent;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class BedrockConversationIntentClassifierTest {

    @Test
    void parsesStructuredCatalogDecisionWithoutAllowingModelDataToBecomeSql() {
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        when(converseClient.complete(anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat()))
                .thenReturn("""
                        {"intent":"CATALOG_SEARCH","confidence":0.94,
                         "catalogQuery":{"name":"camiseta","sku":null,"size":"M","color":"negro"},
                         "policyKey":null}
                        """);

        var decision = new BedrockConversationIntentClassifier(converseClient, new ObjectMapper())
                .classify("¿Tienen camisetas oscuras en mediano?");

        assertEquals(ConversationIntent.CATALOG_SEARCH, decision.intent());
        assertEquals(0.94, decision.confidence());
        assertEquals("camiseta", decision.catalogQuery().name());
        assertEquals("M", decision.catalogQuery().size());
        assertEquals("negro", decision.catalogQuery().color());
    }

    @Test
    void convertsMalformedModelOutputToUnknownDecision() {
        BedrockConverseClient converseClient = mock(BedrockConverseClient.class);
        when(converseClient.complete(anyString(), anyString(), anyString(), anyString(), anyInt(), anyFloat()))
                .thenReturn("not-json");

        var decision = new BedrockConversationIntentClassifier(converseClient, new ObjectMapper())
                .classify("consulta");

        assertEquals(ConversationIntent.UNKNOWN, decision.intent());
        assertEquals(0.0, decision.confidence());
    }
}
