package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.HumanFollowUpPriority;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpStatus;
import com.wally.customersupport.conversation.domain.model.HumanFollowUpTask;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "human_follow_up_tasks", schema = "wcs")
public class HumanFollowUpTaskJpaEntity {

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "source_message_id")
    private UUID sourceMessageId;

    @Column(nullable = false, length = 64)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HumanFollowUpPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HumanFollowUpStatus status;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected HumanFollowUpTaskJpaEntity() {
    }

    public HumanFollowUpTaskJpaEntity(HumanFollowUpTask task) {
        this.id = task.id();
        this.conversationId = task.conversationId();
        this.sourceMessageId = task.sourceMessageId();
        this.reason = task.reason();
        this.priority = task.priority();
        this.status = task.status();
        this.dueAt = task.dueAt();
        this.createdAt = task.createdAt();
        this.updatedAt = task.updatedAt();
        this.completedAt = task.completedAt();
    }

    public HumanFollowUpTask toDomain() {
        return new HumanFollowUpTask(
                id,
                conversationId,
                sourceMessageId,
                reason,
                priority,
                status,
                dueAt,
                createdAt,
                updatedAt,
                completedAt);
    }
}
