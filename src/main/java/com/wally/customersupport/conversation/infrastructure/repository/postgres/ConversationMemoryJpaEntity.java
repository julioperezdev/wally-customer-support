package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.ConversationState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "conversation_memory_states", schema = "wcs")
public class ConversationMemoryJpaEntity {

    @Id
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recent_messages", nullable = false, columnDefinition = "jsonb")
    private List<String> recentMessages;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected ConversationMemoryJpaEntity() {
    }

    public ConversationMemoryJpaEntity(ConversationState state) {
        this.conversationId = state.conversationId();
        this.actorId = state.actorId();
        this.recentMessages = state.recentMessages();
        this.updatedAt = state.updatedAt();
    }

    public void updateFrom(ConversationState state) {
        this.recentMessages = state.recentMessages();
        this.updatedAt = state.updatedAt();
    }

    public ConversationState toDomain() {
        return new ConversationState(
                conversationId,
                actorId,
                recentMessages,
                updatedAt,
                version == null ? 0L : version);
    }

    public UUID conversationId() {
        return conversationId;
    }

    public String actorId() {
        return actorId;
    }

    public long version() {
        return version == null ? 0L : version;
    }
}
