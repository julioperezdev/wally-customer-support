package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.ConversationState;
import com.wally.customersupport.conversation.domain.model.ConversationSummary;
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

    @Column(name = "conversation_summary", columnDefinition = "text")
    private String conversationSummary;

    @Column(name = "summary_version", nullable = false)
    private Long summaryVersion;

    @Column(name = "summarized_message_count", nullable = false)
    private Integer summarizedMessageCount;

    @Column(name = "summary_updated_at")
    private Instant summaryUpdatedAt;

    protected ConversationMemoryJpaEntity() {
    }

    public ConversationMemoryJpaEntity(ConversationState state) {
        this.conversationId = state.conversationId();
        this.actorId = state.actorId();
        this.recentMessages = state.recentMessages();
        this.updatedAt = databaseTimestamp(state.updatedAt());
        updateSummaryFrom(state.summary());
    }

    public void updateFrom(ConversationState state) {
        this.recentMessages = state.recentMessages();
        this.updatedAt = databaseTimestamp(state.updatedAt());
        updateSummaryFrom(state.summary());
    }

    public ConversationState toDomain() {
        ConversationSummary summary = conversationSummary == null
                || conversationSummary.isBlank()
                || summaryUpdatedAt == null
                ? null
                : new ConversationSummary(
                        conversationSummary,
                        summaryVersion == null ? 0L : summaryVersion,
                        summarizedMessageCount == null ? 0 : summarizedMessageCount,
                        summaryUpdatedAt);
        return new ConversationState(
                conversationId,
                actorId,
                recentMessages,
                updatedAt,
                version == null ? 0L : version,
                summary);
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

    private void updateSummaryFrom(ConversationSummary summary) {
        if (summary == null) {
            this.conversationSummary = null;
            this.summaryVersion = 0L;
            this.summarizedMessageCount = 0;
            this.summaryUpdatedAt = null;
            return;
        }
        this.conversationSummary = summary.text();
        this.summaryVersion = summary.version();
        this.summarizedMessageCount = summary.summarizedMessageCount();
        this.summaryUpdatedAt = databaseTimestamp(summary.updatedAt());
    }

    private static Instant databaseTimestamp(Instant timestamp) {
        return timestamp.truncatedTo(ChronoUnit.MICROS);
    }
}
