package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.out.ConversationMemory;
import com.wally.customersupport.conversation.domain.model.ConversationMemoryConflictException;
import com.wally.customersupport.conversation.domain.model.ConversationMemoryOwnershipException;
import com.wally.customersupport.conversation.domain.model.ConversationMemoryPolicy;
import com.wally.customersupport.conversation.domain.model.ConversationState;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(
        name = "wcs.conversation.memory.enabled",
        havingValue = "true",
        matchIfMissing = false)
@RequiredArgsConstructor
public class JpaConversationMemoryAdapter implements ConversationMemory {

    private static final Logger LOGGER = LoggerFactory.getLogger(JpaConversationMemoryAdapter.class);

    private final SpringDataConversationMemoryRepository repository;
    private final ConversationMemoryPolicy policy;
    private final Clock clock;

    @Override
    @Transactional
    public Optional<ConversationState> load(UUID conversationId, String actorId) {
        long startedAt = System.nanoTime();
        if (conversationId == null || actorId == null || actorId.isBlank()) {
            record("MEMORY_STATE_LOADED", "INVALID_INPUT", 0, startedAt, null);
            return Optional.empty();
        }

        Optional<ConversationMemoryJpaEntity> stored = repository.findById(conversationId);
        if (stored.isEmpty() || !stored.get().actorId().equals(actorId.strip())) {
            record("MEMORY_STATE_LOADED", "MISS", 0, startedAt, conversationId);
            return Optional.empty();
        }

        ConversationMemoryJpaEntity entity = stored.get();
        ConversationState state = entity.toDomain();
        if (policy.isExpired(state, clock.instant())) {
            repository.delete(entity);
            repository.flush();
            record("MEMORY_STATE_EXPIRED", "REMOVED", state.recentMessages().size(), startedAt, conversationId);
            return Optional.empty();
        }

        record("MEMORY_STATE_LOADED", "HIT", state.recentMessages().size(), startedAt, conversationId);
        return Optional.of(state);
    }

    @Override
    @Transactional
    public ConversationState save(ConversationState state) {
        long startedAt = System.nanoTime();
        ConversationState normalized = policy.normalize(Objects.requireNonNull(state, "state is required"));
        if (policy.isExpired(normalized, clock.instant())) {
            throw new IllegalArgumentException("expired conversation state cannot be saved");
        }

        Optional<ConversationMemoryJpaEntity> stored = repository.findById(normalized.conversationId());
        if (stored.isPresent()) {
            ConversationMemoryJpaEntity entity = stored.get();
            if (!entity.actorId().equals(normalized.actorId())) {
                throw new ConversationMemoryOwnershipException(
                        "conversation memory belongs to another actor");
            }
            if (entity.version() != normalized.version()) {
                throw new ConversationMemoryConflictException(
                        "conversation memory version is stale");
            }

            entity.updateFrom(normalized);
            try {
                ConversationState saved = repository.saveAndFlush(entity).toDomain();
                record("MEMORY_STATE_SAVED", "UPDATED", saved.recentMessages().size(), startedAt, saved.conversationId());
                return saved;
            } catch (OptimisticLockingFailureException exception) {
                recordConflict(normalized.conversationId(), exception);
                throw new ConversationMemoryConflictException(
                        "conversation memory was updated concurrently", exception);
            }
        }

        try {
            ConversationState saved = repository.saveAndFlush(new ConversationMemoryJpaEntity(normalized)).toDomain();
            record("MEMORY_STATE_SAVED", "CREATED", saved.recentMessages().size(), startedAt, saved.conversationId());
            return saved;
        } catch (OptimisticLockingFailureException exception) {
            recordConflict(normalized.conversationId(), exception);
            throw new ConversationMemoryConflictException(
                    "conversation memory was created concurrently", exception);
        }
    }

    @Override
    @Transactional
    public void clear(UUID conversationId, String actorId) {
        long startedAt = System.nanoTime();
        if (conversationId == null || actorId == null || actorId.isBlank()) {
            record("MEMORY_STATE_CLEARED", "INVALID_INPUT", 0, startedAt, conversationId);
            return;
        }
        repository.findById(conversationId)
                .filter(entity -> entity.actorId().equals(actorId.strip()))
                .ifPresent(entity -> {
                    repository.delete(entity);
                    repository.flush();
                    record("MEMORY_STATE_CLEARED", "REMOVED", 0, startedAt, conversationId);
                });
    }

    private void record(
            String eventType,
            String result,
            int messageCount,
            long startedAt,
            UUID conversationId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("operation", eventType.toLowerCase(java.util.Locale.ROOT));
        fields.put("result", result);
        fields.put("messageCount", messageCount);
        if (startedAt > 0) {
            fields.put("durationMs", (System.nanoTime() - startedAt) / 1_000_000);
        }
        if (conversationId != null) {
            fields.put("correlationId", conversationId);
        }
        StructuredEventLog.info(LOGGER, eventType, fields);
    }

    private void recordConflict(UUID conversationId, RuntimeException exception) {
        StructuredEventLog.warn(LOGGER, "MEMORY_STATE_CONFLICT", Map.of(
                "operation", "conversation.memory.save",
                "result", "CONFLICT",
                "errorType", exception.getClass().getSimpleName(),
                "correlationId", conversationId));
    }
}
