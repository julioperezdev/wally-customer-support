package com.wally.customersupport.conversation.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, short-lived state used to continue a conversation.
 *
 * <p>Channel identifiers and raw provider payloads are deliberately not part
 * of this model. The actor identifier must be an internal or pseudonymous
 * value supplied by the application layer.</p>
 */
public record ConversationState(
        UUID conversationId,
        String actorId,
        List<String> recentMessages,
        Instant updatedAt,
        long version,
        ConversationSummary summary) {

    public ConversationState(
            UUID conversationId,
            String actorId,
            List<String> recentMessages,
            Instant updatedAt) {
        this(conversationId, actorId, recentMessages, updatedAt, 0L, null);
    }

    public ConversationState(
            UUID conversationId,
            String actorId,
            List<String> recentMessages,
            Instant updatedAt,
            long version) {
        this(conversationId, actorId, recentMessages, updatedAt, version, null);
    }

    public ConversationState {
        conversationId = Objects.requireNonNull(conversationId, "conversationId is required");
        actorId = Objects.requireNonNull(actorId, "actorId is required").strip();
        if (actorId.isBlank()) {
            throw new IllegalArgumentException("actorId must not be blank");
        }
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }
}
