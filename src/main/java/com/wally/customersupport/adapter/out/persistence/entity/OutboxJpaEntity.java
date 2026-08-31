package com.wally.customersupport.adapter.out.persistence.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.domain.model.DeliveryType;
import com.wally.customersupport.domain.model.OutboundMessage;
import com.wally.customersupport.domain.model.OutboxMessage;
import com.wally.customersupport.domain.model.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "outbox_messages", schema = "wcs")
public class OutboxJpaEntity {

    private static final String PARAMETER_SEPARATOR = "\u001F";

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 32)
    private DeliveryType messageType;

    @Column(name = "recipient_wa_id", nullable = false, length = 32)
    private String recipientWaId;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "template_name", length = 128)
    private String templateName;

    @Column(name = "template_language_code", length = 32)
    private String templateLanguageCode;

    @Column(name = "template_body_parameters", columnDefinition = "text")
    private String templateBodyParameters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Version
    private long version;

    protected OutboxJpaEntity() {
    }

    public OutboxJpaEntity(OutboxMessage message) {
        this.id = message.id();
        this.aggregateId = message.aggregateId();
        this.eventType = message.eventType();
        this.messageType = message.message().deliveryType();
        this.recipientWaId = message.message().recipientWaId();
        this.body = message.message().body();
        this.templateName = message.message().templateName();
        this.templateLanguageCode = message.message().templateLanguageCode();
        this.templateBodyParameters = String.join(PARAMETER_SEPARATOR, message.message().templateBodyParameters());
        this.status = message.status();
        this.attempts = message.attempts();
        this.availableAt = message.availableAt();
        this.createdAt = message.createdAt();
        this.sentAt = message.sentAt();
        this.lastError = message.lastError();
    }

    public OutboxMessage toDomain() {
        List<String> parameters = templateBodyParameters == null || templateBodyParameters.isEmpty()
                ? List.of()
                : Arrays.asList(templateBodyParameters.split(PARAMETER_SEPARATOR, -1));
        OutboundMessage outboundMessage = new OutboundMessage(
                aggregateId,
                recipientWaId,
                messageType,
                body,
                templateName,
                templateLanguageCode,
                parameters);
        return new OutboxMessage(
                id,
                aggregateId,
                eventType,
                outboundMessage,
                status,
                attempts,
                availableAt,
                createdAt,
                sentAt,
                lastError);
    }

    public void markProcessing() {
        status = OutboxStatus.PROCESSING;
        attempts++;
    }

    public void markSent(Instant sentAt) {
        status = OutboxStatus.SENT;
        this.sentAt = sentAt;
        lastError = null;
    }

    public void markFailed(String error, Instant nextAvailableAt, boolean exhausted) {
        status = exhausted ? OutboxStatus.FAILED : OutboxStatus.PENDING;
        lastError = error;
        availableAt = nextAvailableAt;
    }
}
