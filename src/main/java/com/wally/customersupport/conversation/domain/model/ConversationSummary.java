package com.wally.customersupport.conversation.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Versioned summary of the conversation prefix that is no longer kept in the
 * recent-message window.
 *
 * <p>The summary is context only. It is never an authority for catalog,
 * inventory, cart, order or other transactional data.</p>
 */
public record ConversationSummary(
        String text,
        long version,
        int summarizedMessageCount,
        Instant updatedAt) {

    public ConversationSummary {
        text = Objects.requireNonNull(text, "text is required").strip();
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        if (summarizedMessageCount < 0) {
            throw new IllegalArgumentException("summarizedMessageCount must not be negative");
        }
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }
}
