package com.wally.customersupport.conversation.application.service;

import java.time.Instant;

import com.wally.customersupport.conversation.domain.model.ConversationWorkingMemory;

/** Pure state transition for the bounded catalog memory carried by a turn. */
final class ConversationWorkingMemoryReducer {

    Transition reduce(
            ConversationWorkingMemory previous,
            ConversationWorkingMemory observed,
            boolean generalCatalogRequest,
            Instant updatedAt) {
        ConversationWorkingMemory prior = previous == null ? ConversationWorkingMemory.empty() : previous;
        ConversationWorkingMemory current = observed == null ? ConversationWorkingMemory.empty() : observed;

        if (current.isCleared()) {
            return new Transition(ConversationWorkingMemory.empty(), Outcome.RESET);
        }
        if (generalCatalogRequest) {
            ConversationWorkingMemory replacement = snapshot(current, updatedAt);
            return new Transition(
                    replacement,
                    replacement.hasCandidates() ? Outcome.UPDATED : Outcome.RESET);
        }
        if (!current.hasCatalogObservation()) {
            return new Transition(prior, Outcome.UNCHANGED);
        }

        ConversationWorkingMemory replacement = snapshot(current, updatedAt);
        return new Transition(
                replacement,
                replacement.hasCandidates() ? Outcome.UPDATED : Outcome.CANDIDATES_CLEARED);
    }

    private static ConversationWorkingMemory snapshot(ConversationWorkingMemory memory, Instant updatedAt) {
        if (!memory.hasCatalogObservation()) {
            return ConversationWorkingMemory.empty();
        }
        return new ConversationWorkingMemory(
                memory.catalogCandidates(),
                memory.focusedSku(),
                memory.lastCatalogStatus(),
                updatedAt);
    }

    record Transition(ConversationWorkingMemory memory, Outcome outcome) {
        boolean resetsSelection() {
            return outcome == Outcome.RESET;
        }
    }

    enum Outcome {
        UPDATED,
        CANDIDATES_CLEARED,
        RESET,
        UNCHANGED
    }
}
