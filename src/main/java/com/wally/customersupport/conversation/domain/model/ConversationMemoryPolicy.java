package com.wally.customersupport.conversation.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Retention and context limits applied before conversational state is stored.
 */
public record ConversationMemoryPolicy(
        Duration ttl,
        int maxMessages,
        int maxMessageCharacters) {

    public ConversationMemoryPolicy {
        ttl = Objects.requireNonNull(ttl, "ttl is required");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (maxMessages <= 0) {
            throw new IllegalArgumentException("maxMessages must be positive");
        }
        if (maxMessageCharacters <= 0) {
            throw new IllegalArgumentException("maxMessageCharacters must be positive");
        }
    }

    /**
     * Initial WCS recommendation for the short-lived context, pending legal
     * and business approval of the final retention policy.
     */
    public static ConversationMemoryPolicy recommended() {
        return new ConversationMemoryPolicy(Duration.ofHours(24), 20, 2_000);
    }

    public ConversationState normalize(ConversationState state) {
        Objects.requireNonNull(state, "state is required");

        List<String> boundedMessages = state.recentMessages().stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(message -> !message.isBlank())
                .map(this::limitMessage)
                .toList();

        int firstMessage = Math.max(0, boundedMessages.size() - maxMessages);
        return new ConversationState(
                state.conversationId(),
                state.actorId(),
                boundedMessages.subList(firstMessage, boundedMessages.size()),
                state.updatedAt());
    }

    public boolean isExpired(ConversationState state, Instant now) {
        Objects.requireNonNull(state, "state is required");
        Objects.requireNonNull(now, "now is required");
        return !now.isBefore(state.updatedAt().plus(ttl));
    }

    private String limitMessage(String message) {
        return message.length() <= maxMessageCharacters
                ? message
                : message.substring(0, maxMessageCharacters);
    }
}
