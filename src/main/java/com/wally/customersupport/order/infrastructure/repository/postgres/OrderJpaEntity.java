package com.wally.customersupport.order.infrastructure.repository.postgres;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.order.domain.model.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders", schema = "wcs")
public class OrderJpaEntity {

    @Id
    private UUID id;

    @Column(name = "customer_reference", length = 128)
    private String customerReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "payment_provider", nullable = false, length = 32)
    private String paymentProvider;

    @Column(name = "payment_preference_id", length = 128)
    private String paymentPreferenceId;

    @Column(name = "payment_url", length = 2048)
    private String paymentUrl;

    @Column(name = "external_payment_id", length = 128)
    private String externalPaymentId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemJpaEntity> items = new ArrayList<>();

    protected OrderJpaEntity() {
    }

    public OrderJpaEntity(
            UUID id,
            String customerReference,
            String currency,
            BigDecimal total,
            String paymentProvider,
            String idempotencyKey,
            String requestHash,
            Instant createdAt) {
        this.id = id;
        this.customerReference = customerReference;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.currency = currency;
        this.total = total;
        this.paymentProvider = paymentProvider;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void addItem(
            String sku,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            String currency,
            BigDecimal lineTotal) {
        items.add(new OrderItemJpaEntity(this, sku, productName, quantity, unitPrice, currency, lineTotal));
    }

    public void attachPaymentPreference(String preferenceId, String checkoutUrl, Instant now) {
        this.paymentPreferenceId = preferenceId;
        this.paymentUrl = checkoutUrl;
        this.updatedAt = now;
    }

    public void applyPayment(
            OrderStatus nextStatus,
            String providerPaymentId,
            Instant now) {
        if (status.isTerminal() && status != nextStatus) {
            return;
        }
        this.status = nextStatus;
        this.externalPaymentId = providerPaymentId;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerReference() {
        return customerReference;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public String getPaymentPreferenceId() {
        return paymentPreferenceId;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }

    public String getExternalPaymentId() {
        return externalPaymentId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<OrderItemJpaEntity> getItems() {
        return List.copyOf(items);
    }
}
