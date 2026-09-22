package com.wally.customersupport.conversation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import com.wally.customersupport.conversation.domain.model.CatalogCandidateReference;
import com.wally.customersupport.conversation.domain.model.CatalogObservationStatus;
import com.wally.customersupport.conversation.domain.model.ConversationWorkingMemory;
import org.junit.jupiter.api.Test;

class ConversationWorkingMemoryReducerTest {

    private static final Instant NOW = Instant.parse("2026-09-21T12:00:00Z");
    private final ConversationWorkingMemoryReducer reducer = new ConversationWorkingMemoryReducer();

    @Test
    void replacesCandidatesOnAnAuthoritativeCatalogObservation() {
        var transition = reducer.reduce(
                memory("RP-OLD"),
                ConversationWorkingMemory.catalogObservation(
                        List.of(candidate("RP-NEW")), CatalogObservationStatus.MATCHED, null),
                false,
                NOW);

        assertEquals(ConversationWorkingMemoryReducer.Outcome.UPDATED, transition.outcome());
        assertEquals("RP-NEW", transition.memory().focusedSku());
        assertEquals(NOW, transition.memory().updatedAt());
    }

    @Test
    void clearsStaleCandidatesWhenTheLatestLookupHasNoMatches() {
        var transition = reducer.reduce(
                memory("RP-OLD"),
                ConversationWorkingMemory.catalogObservation(
                        List.of(), CatalogObservationStatus.NO_MATCH, null),
                false,
                NOW);

        assertEquals(ConversationWorkingMemoryReducer.Outcome.CANDIDATES_CLEARED, transition.outcome());
        assertFalse(transition.memory().hasCandidates());
        assertEquals(CatalogObservationStatus.NO_MATCH, transition.memory().lastCatalogStatus());
    }

    @Test
    void explicitConversationResetClearsMemoryAndSelection() {
        var transition = reducer.reduce(memory("RP-OLD"), ConversationWorkingMemory.cleared(NOW), false, NOW);

        assertEquals(ConversationWorkingMemoryReducer.Outcome.RESET, transition.outcome());
        assertEquals(ConversationWorkingMemory.empty(), transition.memory());
        assertTrue(transition.resetsSelection());
    }

    @Test
    void nonCatalogTurnPreservesThePreviousObservation() {
        ConversationWorkingMemory previous = memory("RP-OLD");

        var transition = reducer.reduce(previous, ConversationWorkingMemory.empty(), false, NOW);

        assertEquals(ConversationWorkingMemoryReducer.Outcome.UNCHANGED, transition.outcome());
        assertEquals(previous, transition.memory());
    }

    private static ConversationWorkingMemory memory(String sku) {
        return ConversationWorkingMemory.catalogObservation(
                List.of(candidate(sku)), CatalogObservationStatus.MATCHED, NOW.minusSeconds(1));
    }

    private static CatalogCandidateReference candidate(String sku) {
        return new CatalogCandidateReference("Remera", sku, "M", "Negro");
    }
}
