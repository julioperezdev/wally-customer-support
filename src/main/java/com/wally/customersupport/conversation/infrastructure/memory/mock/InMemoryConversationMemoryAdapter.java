package com.wally.customersupport.conversation.infrastructure.memory.mock;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.wally.customersupport.conversation.application.port.out.ConversationMemory;
import com.wally.customersupport.conversation.domain.model.ConversationMemoryPolicy;
import com.wally.customersupport.conversation.domain.model.ConversationState;

/**
 * In-memory adapter used by contract tests and controlled local development.
 * It is intentionally not a Spring component and must not be used as
 * production storage. PostgreSQL is the production-capable adapter, subject
 * to the explicit memory activation gate.
 */
public final class InMemoryConversationMemoryAdapter implements ConversationMemory {

    private final Map<MemoryKey, ConversationState> states = new ConcurrentHashMap<>();
    private final ConversationMemoryPolicy policy;
    private final Clock clock;

    public InMemoryConversationMemoryAdapter(ConversationMemoryPolicy policy) {
        this(policy, Clock.systemUTC());
    }

    public InMemoryConversationMemoryAdapter(ConversationMemoryPolicy policy, Clock clock) {
        this.policy = Objects.requireNonNull(policy, "policy is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public Optional<ConversationState> load(UUID conversationId, String actorId) {
        MemoryKey key = keyOf(conversationId, actorId);
        if (key == null) {
            return Optional.empty();
        }

        ConversationState state = states.get(key);
        if (state == null) {
            return Optional.empty();
        }
        if (policy.isExpired(state, clock.instant())) {
            states.remove(key, state);
            return Optional.empty();
        }
        return Optional.of(state);
    }

    @Override
    public ConversationState save(ConversationState state) {
        ConversationState normalized = policy.normalize(Objects.requireNonNull(state, "state is required"));
        if (policy.isExpired(normalized, clock.instant())) {
            throw new IllegalArgumentException("expired conversation state cannot be saved");
        }
        states.put(new MemoryKey(normalized.conversationId(), normalized.actorId()), normalized);
        return normalized;
    }

    @Override
    public void clear(UUID conversationId, String actorId) {
        MemoryKey key = keyOf(conversationId, actorId);
        if (key != null) {
            states.remove(key);
        }
    }

    private static MemoryKey keyOf(UUID conversationId, String actorId) {
        if (conversationId == null || actorId == null || actorId.isBlank()) {
            return null;
        }
        return new MemoryKey(conversationId, actorId.strip());
    }

    private record MemoryKey(UUID conversationId, String actorId) {
    }
}
