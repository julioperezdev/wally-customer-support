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
import com.wally.customersupport.conversation.domain.model.CatalogCandidateReference;
import com.wally.customersupport.conversation.domain.model.ConversationWorkingMemory;
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

    @Test
    void storesCandidatesFromTheCatalogResultAndFocusesOnlyAnUnambiguousVariant() {
        UUID conversationId = UUID.randomUUID();
        ConversationState current = new ConversationState(
                conversationId, "actor-1", List.of("quiero la campera"), NOW);
        ConversationContext context = new ConversationContext(
                conversationId, "customer", "quiero la campera", current.recentMessages(),
                List.of(), null, List.of(), Channel.TELEGRAM, current.selection());
        ConversationWorkingMemory observed = ConversationWorkingMemory.catalogObservation(
                List.of(new CatalogCandidateReference(
                        "Campera Deploy Friday", "RP-CAM-DF-AZU-M", "M", "Azul", "media/campera.jpg")),
                "MATCHED",
                null);
        ConversationExecutionResult result = new ConversationExecutionResult(
                "workflow", "CATALOG_SEARCH", "REPLIED", "resultado", null, 1, null, observed);

        ConversationState updated = new ConversationSelectionStateService()
                .update(current, context, result, NOW.plusSeconds(1));

        assertEquals("RP-CAM-DF-AZU-M", updated.selection().selectedVariantSku());
        assertEquals("RP-CAM-DF-AZU-M", updated.selection().workingMemory().focusedSku());
        assertEquals(1, updated.selection().workingMemory().catalogCandidates().size());
        assertEquals(NOW.plusSeconds(1), updated.selection().workingMemory().updatedAt());
    }

    @Test
    void retainsGeneralCatalogCandidatesSoTheCustomerCanReferToAListedItem() {
        UUID conversationId = UUID.randomUUID();
        ConversationState current = new ConversationState(
                conversationId,
                "actor-1",
                List.of("que venden"),
                NOW,
                0L,
                null,
                new ConversationSelection(
                        ConversationIntent.CATALOG_SEARCH,
                        ConversationAction.CATALOG_SEARCH,
                        new CatalogQuery(null, null, null, null, "buzo"),
                        null,
                        "CATALOG_SEARCH"));
        ConversationContext context = new ConversationContext(
                conversationId, "customer", "que venden", current.recentMessages(),
                List.of(), null, List.of(), Channel.TELEGRAM, current.selection());
        ConversationWorkingMemory observed = ConversationWorkingMemory.catalogObservation(
                List.of(new CatalogCandidateReference(
                        "Campera Deploy Friday", "RP-CAM-DF-AZU-M", "M", "Azul", null)),
                "MATCHED",
                null);
        ConversationExecutionResult result = new ConversationExecutionResult(
                "workflow", "CATALOG_SEARCH", "REPLIED", "catalogo", null, 1, null, observed);

        ConversationState updated = new ConversationSelectionStateService()
                .update(current, context, result, NOW.plusSeconds(1));

        assertEquals(true, updated.selection().catalogQuery().isEmpty());
        assertEquals("RP-CAM-DF-AZU-M", updated.selection().workingMemory().focusedSku());
    }

    @Test
    void clearsWorkingMemoryAfterSuccessfulPurchaseResult() {
        UUID conversationId = UUID.randomUUID();
        ConversationWorkingMemory memory = ConversationWorkingMemory.catalogObservation(
                List.of(new CatalogCandidateReference(
                        "Campera Deploy Friday", "RP-CAM-DF-AZU-M", "M", "Azul", null)),
                "MATCHED",
                NOW);
        ConversationSelection selection = new ConversationSelection(
                ConversationIntent.CATALOG_SEARCH,
                ConversationAction.CATALOG_SEARCH,
                new CatalogQuery(null, null, null, null, "campera"),
                "RP-CAM-DF-AZU-M",
                "CATALOG_SEARCH",
                memory);
        ConversationState current = new ConversationState(
                conversationId, "actor-1", List.of("comprar la campera"), NOW, 0L, null, selection);
        ConversationContext context = new ConversationContext(
                conversationId, "customer", "comprar la campera", current.recentMessages(),
                List.of(), null, List.of(), Channel.TELEGRAM, selection);
        ConversationExecutionResult result = new ConversationExecutionResult(
                "workflow",
                "PURCHASE_LINK",
                "REPLIED",
                "Listo, link creado",
                null,
                1,
                null,
                ConversationWorkingMemory.cleared(NOW.plusSeconds(1)));

        ConversationState updated = new ConversationSelectionStateService()
                .update(current, context, result, NOW.plusSeconds(2));

        assertEquals(true, updated.selection().catalogQuery().isEmpty());
        assertEquals(null, updated.selection().selectedVariantSku());
        assertEquals(false, updated.selection().workingMemory().hasCandidates());
    }

    @Test
    void clearsOldCandidatesAndFocusedSkuAfterNoCatalogMatches() {
        UUID conversationId = UUID.randomUUID();
        ConversationWorkingMemory memory = ConversationWorkingMemory.catalogObservation(
                List.of(new CatalogCandidateReference(
                        "Campera Deploy Friday", "RP-CAM-DF-AZU-M", "M", "Azul", null)),
                "MATCHED",
                NOW);
        ConversationSelection selection = new ConversationSelection(
                ConversationIntent.CATALOG_SEARCH,
                ConversationAction.CATALOG_SEARCH,
                new CatalogQuery(null, null, null, null, "campera"),
                "RP-CAM-DF-AZU-M",
                "CATALOG_SEARCH",
                memory);
        ConversationState current = new ConversationState(
                conversationId, "actor-1", List.of("quiero la campera roja"), NOW, 0L, null, selection);
        ConversationContext context = new ConversationContext(
                conversationId, "customer", "quiero la campera roja", current.recentMessages(),
                List.of(), null, List.of(), Channel.TELEGRAM, selection);
        ConversationExecutionResult result = new ConversationExecutionResult(
                "workflow",
                "CATALOG_SEARCH",
                "REPLIED",
                "No encontré coincidencias",
                null,
                1,
                null,
                ConversationWorkingMemory.catalogObservation(List.of(), "NO_MATCH", null));

        ConversationState updated = new ConversationSelectionStateService()
                .update(current, context, result, NOW.plusSeconds(1));

        assertEquals(null, updated.selection().selectedVariantSku());
        assertEquals(false, updated.selection().workingMemory().hasCandidates());
        assertEquals("NO_MATCH", updated.selection().workingMemory().lastCatalogStatus());
    }
}
