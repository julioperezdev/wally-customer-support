package com.wally.customersupport.adapter.out.persistence.entity;

import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.domain.model.Message;
import com.wally.customersupport.domain.model.MessageDirection;
import com.wally.customersupport.domain.model.MessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "messages", schema = "wcs")
public class MessageJpaEntity {

    @Id
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "external_message_id", nullable = false, length = 128)
    private String externalMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 32)
    private MessageType messageType;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MessageJpaEntity() {
    }

    public MessageJpaEntity(Message message) {
        this.id = message.id();
        this.conversationId = message.conversationId();
        this.externalMessageId = message.externalMessageId();
        this.direction = message.direction();
        this.messageType = message.messageType();
        this.body = message.body();
        this.occurredAt = message.occurredAt();
        this.createdAt = message.createdAt();
    }

    public Message toDomain() {
        return new Message(
                id,
                conversationId,
                externalMessageId,
                direction,
                messageType,
                body,
                occurredAt,
                createdAt);
    }

    public String body() {
        return body;
    }
}
