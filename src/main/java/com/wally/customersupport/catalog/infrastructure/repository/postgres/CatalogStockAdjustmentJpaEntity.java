package com.wally.customersupport.catalog.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "catalog_stock_adjustments", schema = "wcs")
public class CatalogStockAdjustmentJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String sku;

    @Column(name = "previous_stock", nullable = false)
    private int previousStock;

    @Column(nullable = false)
    private int delta;

    @Column(name = "new_stock", nullable = false)
    private int newStock;

    @Column(nullable = false, length = 256)
    private String reason;

    @Column(name = "actor_key", nullable = false, length = 128)
    private String actorKey;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CatalogStockAdjustmentJpaEntity() {
    }

    public CatalogStockAdjustmentJpaEntity(
            String sku,
            int previousStock,
            int delta,
            int newStock,
            String reason,
            String actorKey,
            String idempotencyKey,
            Instant createdAt) {
        this.id = UUID.randomUUID();
        this.sku = sku;
        this.previousStock = previousStock;
        this.delta = delta;
        this.newStock = newStock;
        this.reason = reason;
        this.actorKey = actorKey;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }

    public StockAdjustmentView toView() {
        return new StockAdjustmentView(id, sku, previousStock, delta, newStock, reason, actorKey, idempotencyKey, createdAt);
    }

    public record StockAdjustmentView(
            UUID id,
            String sku,
            int previousStock,
            int delta,
            int newStock,
            String reason,
            String actorKey,
            String idempotencyKey,
            Instant createdAt) {
    }
}
