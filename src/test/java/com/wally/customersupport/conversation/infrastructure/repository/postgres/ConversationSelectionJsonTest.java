package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ConversationSelectionJsonTest {

    @Test
    void loadsSelectionJsonWrittenBeforeWorkingMemoryWasAdded() throws Exception {
        String legacyJson = """
                {
                  "intent": "CATALOG_SEARCH",
                  "action": "CATALOG_SEARCH",
                  "catalogQuery": {"productType": "campera"},
                  "selectedVariantSku": "RP-CAM-DF-AZU-M",
                  "stage": "CATALOG_SEARCH"
                }
                """;

        ConversationSelectionJson persisted = new ObjectMapper()
                .readValue(legacyJson, ConversationSelectionJson.class);
        var selection = persisted.toDomain();

        assertFalse(selection.workingMemory().hasCandidates());
        assertNull(selection.workingMemory().focusedSku());
    }

    @Test
    void readsLegacyWorkingMemoryFieldsButDoesNotWriteThemAgain() throws Exception {
        String legacyJson = """
                {
                  "intent": "CATALOG_SEARCH",
                  "action": "CATALOG_SEARCH",
                  "catalogQuery": {},
                  "stage": "CATALOG_SEARCH",
                  "workingMemory": {
                    "catalogCandidates": [{
                      "productName": "Remera NullPointer",
                      "sku": "RP-REM-NP-NEG-M",
                      "size": "M",
                      "color": "Negro",
                      "imageReference": "media/legacy.jpg"
                    }],
                    "focusedSku": "RP-REM-NP-NEG-M",
                    "pendingAction": "CATALOG_SEARCH",
                    "lastCatalogStatus": "MATCHED",
                    "updatedAt": "2026-09-21T12:00:00Z"
                  }
                }
                """;

        ObjectMapper mapper = new ObjectMapper();
        ConversationSelectionJson persisted = mapper.readValue(legacyJson, ConversationSelectionJson.class);
        var selection = persisted.toDomain();
        String rewritten = mapper.writeValueAsString(ConversationSelectionJson.fromDomain(selection));

        assertEquals("RP-REM-NP-NEG-M", selection.workingMemory().focusedSku());
        assertEquals("MATCHED", selection.workingMemory().lastCatalogStatus().name());
        assertEquals("Remera NullPointer", selection.workingMemory().focusedCandidate().orElseThrow().productName());
        assertFalse(rewritten.contains("pendingAction"));
        assertFalse(rewritten.contains("imageReference"));
    }
}
