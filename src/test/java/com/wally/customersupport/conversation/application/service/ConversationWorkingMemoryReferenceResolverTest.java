package com.wally.customersupport.conversation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.CatalogCandidateReference;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.conversation.domain.model.ConversationSelection;
import com.wally.customersupport.conversation.domain.model.ConversationWorkingMemory;
import org.junit.jupiter.api.Test;

class ConversationWorkingMemoryReferenceResolverTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private final ConversationWorkingMemoryReferenceResolver resolver =
            new ConversationWorkingMemoryReferenceResolver();

    @Test
    void resolvesSingularReferencesToTheOnlyRecentCatalogCandidate() {
        ConversationContext context = context(
                "Quiero una",
                List.of(candidate("Campera Deploy Friday", "RP-CAM-DF-AZU-M", "M", "Azul")));

        var query = resolver.resolve(context).orElseThrow();

        assertEquals("RP-CAM-DF-AZU-M", query.sku());
    }

    @Test
    void resolvesOrdinalReferenceAgainstTheBoundedDisplayedOrder() {
        ConversationContext context = context(
                "Quiero el segundo",
                List.of(
                        candidate("Buzo Spring Boot", "RP-BUZ-SB-GRI-L", "L", "Gris"),
                        candidate("Buzo Spring Boot", "RP-BUZ-SB-NEG-XL", "XL", "Negro")));

        var query = resolver.resolve(context).orElseThrow();

        assertEquals("RP-BUZ-SB-NEG-XL", query.sku());
    }

    @Test
    void doesNotGuessWhenSingularReferenceHasSeveralCandidatesAndNoFocus() {
        ConversationContext context = context(
                "Quiero esa",
                List.of(
                        candidate("Buzo Spring Boot", "RP-BUZ-SB-GRI-L", "L", "Gris"),
                        candidate("Buzo Spring Boot", "RP-BUZ-SB-NEG-XL", "XL", "Negro")));

        assertTrue(resolver.resolve(context).isEmpty());
    }

    @Test
    void resolvesCartPronounAgainstAUniqueProductEvenWhenTheMessageRepeatsItsCategory() {
        ConversationContext context = context(
                "Prosigamos con la campera, agregala al carrito",
                List.of(candidate("Campera Deploy Friday", "RP-CAM-DF-AZU-M", "M", "Azul")));

        var query = resolver.resolveForCartMutation(context).orElseThrow();

        assertEquals("RP-CAM-DF-AZU-M", query.sku());
    }

    @Test
    void resolvesAnExplicitCategoryAgainstTheUniqueMatchingRecentCandidate() {
        ConversationContext context = context(
                "Me gusta la campera, vamos con esa",
                List.of(
                        candidate("Buzo Spring Boot", "RP-BUZ-SB-GRI-L", "L", "Gris"),
                        candidate("Campera Deploy Friday", "RP-CAM-DF-AZU-M", "M", "Azul"),
                        candidate("Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro")));

        var query = resolver.resolve(context).orElseThrow();

        assertEquals("RP-CAM-DF-AZU-M", query.sku());
    }

    @Test
    void doesNotResolveARecentCandidateWhenExplicitColorConflicts() {
        ConversationContext context = context(
                "Agrega esa campera roja al carrito",
                List.of(candidate("Campera Deploy Friday", "RP-CAM-DF-AZU-M", "M", "Azul")));

        assertTrue(resolver.resolveForCartMutation(context).isEmpty());
    }

    @Test
    void resolvesAUniqueRecentCandidateByItsCurrentVariantFilters() {
        ConversationContext context = context(
                "Quiero el buzo negro",
                List.of(
                        candidate("Buzo Spring Boot", "RP-BUZ-SB-GRI-L", "L", "Gris"),
                        candidate("Buzo Spring Boot", "RP-BUZ-SB-NEG-XL", "XL", "Negro")));

        var query = resolver.resolve(context).orElseThrow();

        assertEquals("RP-BUZ-SB-NEG-XL", query.sku());
    }

    @Test
    void deterministicRouterUsesWorkingMemoryWhenTheModelCannotClassifyAReference() {
        ConversationContext context = context(
                "Quiero una",
                List.of(candidate("Campera Deploy Friday", "RP-CAM-DF-AZU-M", "M", "Azul")));

        var result = new ConversationDecisionReconciler().reconcile(
                context,
                ConversationIntentDecision.unknown());

        assertEquals(ConversationIntent.CATALOG_SEARCH, result.decision().intent());
        assertEquals(ConversationAction.CATALOG_SEARCH, result.decision().action());
        assertEquals("RP-CAM-DF-AZU-M", result.decision().catalogQuery().sku());
        assertEquals("WORKING_MEMORY_REFERENCE", result.strategy());
    }

    private static ConversationContext context(String message, List<CatalogCandidateReference> candidates) {
        ConversationWorkingMemory workingMemory = ConversationWorkingMemory.catalogObservation(
                candidates,
                "MATCHED",
                NOW);
        ConversationSelection selection = new ConversationSelection(
                ConversationIntent.CATALOG_SEARCH,
                ConversationAction.CATALOG_SEARCH,
                com.wally.customersupport.catalog.domain.model.CatalogQuery.empty(),
                workingMemory.focusedSku(),
                "CATALOG_SEARCH",
                workingMemory);
        return new ConversationContext(
                UUID.randomUUID(),
                "test-actor",
                message,
                List.of(),
                List.of(),
                null,
                List.of(),
                Channel.TELEGRAM,
                selection);
    }

    private static CatalogCandidateReference candidate(String name, String sku, String size, String color) {
        return new CatalogCandidateReference(name, sku, size, color, null);
    }
}
