package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.ContactSuppression;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "contact_suppressions", schema = "wcs")
public class ContactSuppressionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "actor_key", nullable = false, unique = true, length = 64)
    private String actorKey;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 64)
    private String reason;

    @Column(name = "source_message_id")
    private UUID sourceMessageId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContactSuppressionJpaEntity() {
    }

    public ContactSuppressionJpaEntity(ContactSuppression suppression) {
        this.id = suppression.id();
        this.actorKey = suppression.actorKey();
        this.status = suppression.status();
        this.reason = suppression.reason();
        this.sourceMessageId = suppression.sourceMessageId();
        this.createdAt = suppression.createdAt();
        this.updatedAt = suppression.updatedAt();
    }

    public ContactSuppression toDomain() {
        return new ContactSuppression(id, actorKey, status, reason, sourceMessageId, createdAt, updatedAt);
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
