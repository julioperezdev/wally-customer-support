package com.wally.customersupport.adapter.out.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.domain.model.Channel;
import com.wally.customersupport.domain.model.Conversation;
import com.wally.customersupport.domain.model.ConversationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversations", schema = "wcs")
public class ConversationJpaEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Channel channel;

    @Column(name = "external_conversation_id", nullable = false, length = 128)
    private String externalConversationId;

    @Column(name = "customer_wa_id", nullable = false, length = 32)
    private String customerWaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConversationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationJpaEntity() {
    }

    public ConversationJpaEntity(Conversation conversation) {
        this.id = conversation.id();
        this.channel = conversation.channel();
        this.externalConversationId = conversation.externalConversationId();
        this.customerWaId = conversation.customerWaId();
        this.status = conversation.status();
        this.createdAt = conversation.createdAt();
        this.updatedAt = conversation.updatedAt();
    }

    public Conversation toDomain() {
        return new Conversation(id, channel, externalConversationId, customerWaId, status, createdAt, updatedAt);
    }
}
