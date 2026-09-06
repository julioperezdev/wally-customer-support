package com.wally.customersupport.conversation.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.ConversationState;

/**
 * Storage-neutral contract for short-lived conversational state.
 *
 * <p>The actor is part of every read and delete operation so an adapter cannot
 * accidentally expose state belonging to another customer or conversation.
 * Implementations must apply their configured retention policy when loading
 * state.</p>
 */
public interface ConversationMemory {

    Optional<ConversationState> load(UUID conversationId, String actorId);

    ConversationState save(ConversationState state);

    void clear(UUID conversationId, String actorId);
}
