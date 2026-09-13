package com.wally.customersupport.order.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_events", schema = "wcs")
public class PaymentEventJpaEntity {

    @Id
    private UUID id;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "provider_event_id", nullable = false, length = 160)
    private String providerEventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "payment_id", length = 128)
    private String paymentId;

    @Column(name = "payment_status", length = 32)
    private String paymentStatus;

    @Column(name = "payload_hash", nullable = false, length = 64)
    private String payloadHash;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentEventJpaEntity() {
    }

    public PaymentEventJpaEntity(
            UUID orderId,
            String provider,
            String providerEventId,
            String eventType,
            String paymentId,
            String paymentStatus,
            String payloadHash,
            Instant occurredAt,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.eventType = eventType;
        this.paymentId = paymentId;
        this.paymentStatus = paymentStatus;
        this.payloadHash = payloadHash;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
    }
}
