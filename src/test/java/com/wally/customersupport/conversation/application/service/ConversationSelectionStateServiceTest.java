package com.wally.customersupport.conversation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionResult;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationSelection;
import com.wally.customersupport.conversation.domain.model.ConversationState;
import org.junit.jupiter.api.Test;

class ConversationSelectionStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-18T22:00:00Z");

    @Test
    void projectsStructuredCatalogSelectionWithoutTurningItIntoFacts() {
        UUID conversationId = UUID.randomUUID();
        CatalogQuery previousQuery = new CatalogQuery(null, null, null, null, "buzo");
        ConversationState current = new ConversationState(
                conversationId,
                "actor-1",
                List.of("que sea negro", "quiero un buzo"),
                NOW,
                0L,
                null,
                new ConversationSelection(
                        ConversationIntent.CATALOG_SEARCH,
                        ConversationAction.CATALOG_SEARCH,
                        previousQuery,
                        null,
                        "CATALOG_SEARCH"));
        ConversationContext context = new ConversationContext(
                conversationId,
                "customer",
                "que sea negro",
                current.recentMessages(),
                List.of(),
                null,
                List.of(),
                Channel.TELEGRAM,
                current.selection());
        ConversationExecutionResult result = new ConversationExecutionResult(
                "wcs-agent-runtime-v1",
                "CATALOG_SEARCH",
                "REPLIED",
                "respuesta",
                null,
                1);

        ConversationState updated = new ConversationSelectionStateService()
                .update(current, context, result, NOW.plusSeconds(1));

        assertEquals("buzo", updated.selection().catalogQuery().productType());
        assertEquals("negro", updated.selection().catalogQuery().color());
        assertEquals(ConversationIntent.CATALOG_SEARCH, updated.selection().intent());
        assertEquals("CATALOG_SEARCH", updated.selection().stage());
    }

    @Test
    void clearsStaleCatalogSelectionWhenCustomerRequestsTheWholeCatalog() {
        UUID conversationId = UUID.randomUUID();
        ConversationSelection selection = new ConversationSelection(
                ConversationIntent.CATALOG_SEARCH,
                ConversationAction.CATALOG_SEARCH,
                new CatalogQuery("nullpointer", null, "M", "negro", "remera"),
                "RP-REM-NP-NEG-M",
                "CATALOG_SEARCH");
        ConversationState current = new ConversationState(
                conversationId,
                "actor-1",
                List.of("Tenes ropa", "Busco una remera negra talle M"),
                NOW,
                0L,
                null,
                selection);
        ConversationContext context = new ConversationContext(
                conversationId,
                "customer",
                "Tenes ropa",
                current.recentMessages(),
                List.of(),
                null,
                List.of(),
                Channel.TELEGRAM,
                selection);
        ConversationExecutionResult result = new ConversationExecutionResult(
                "wcs-agent-runtime-v1",
                "CATALOG_SEARCH",
                "REPLIED",
                "catalogo",
                null,
                1);

        ConversationState updated = new ConversationSelectionStateService()
                .update(current, context, result, NOW.plusSeconds(1));

        assertEquals(true, updated.selection().catalogQuery().isEmpty());
        assertEquals(null, updated.selection().selectedVariantSku());
    }
}
