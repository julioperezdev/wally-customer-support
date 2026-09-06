package com.wally.customersupport.conversation.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.Locale;

/**
 * A low-risk preference that may help conversational context without becoming
 * an authority for transactional data.
 */
public record CustomerPreference(
        UUID conversationId,
        String actorId,
        String key,
        String value,
        PreferenceScope scope,
        double confidence,
        PreferenceOrigin origin,
        boolean confirmed,
        Instant updatedAt,
        Instant expiresAt) {

    public CustomerPreference {
        actorId = Objects.requireNonNull(actorId, "actorId is required").strip();
        key = Objects.requireNonNull(key, "key is required").strip().toLowerCase(Locale.ROOT);
        value = Objects.requireNonNull(value, "value is required").strip();
        scope = Objects.requireNonNull(scope, "scope is required");
        origin = Objects.requireNonNull(origin, "origin is required");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
        if (actorId.isBlank()) {
            throw new IllegalArgumentException("actorId must not be blank");
        }
        if (key.isBlank() || value.isBlank()) {
            throw new IllegalArgumentException("preference key and value must not be blank");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
        if (scope == PreferenceScope.ACTOR && conversationId != null) {
            throw new IllegalArgumentException("actor preferences must not have a conversation id");
        }
        if (scope == PreferenceScope.CONVERSATION && conversationId == null) {
            throw new IllegalArgumentException("conversation preferences require a conversation id");
        }
        if (!confirmed) {
            throw new IllegalArgumentException("only confirmed preferences may be stored");
        }
        if (!expiresAt.isAfter(updatedAt)) {
            throw new IllegalArgumentException("expiresAt must be after updatedAt");
        }
    }
}
