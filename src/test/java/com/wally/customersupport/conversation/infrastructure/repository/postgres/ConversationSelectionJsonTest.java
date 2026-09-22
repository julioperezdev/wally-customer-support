package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
}
