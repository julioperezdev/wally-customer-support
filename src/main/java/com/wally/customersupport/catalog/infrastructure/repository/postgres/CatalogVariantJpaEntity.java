package com.wally.customersupport.catalog.infrastructure.repository.postgres;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.catalog.domain.model.CatalogVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "catalog_variants", schema = "wcs")
public class CatalogVariantJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private CatalogProductJpaEntity product;

    @Column(nullable = false, unique = true, length = 80)
    private String sku;

    @Column(name = "size_label", nullable = false, length = 32)
    private String sizeLabel;

    @Column(nullable = false, length = 64)
    private String color;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CatalogVariantJpaEntity() {
    }

    public CatalogVariant toDomain() {
        return new CatalogVariant(id, sku, sizeLabel, color, price, currency, stock, active);
    }

    public boolean isActive() {
        return active;
    }

    public String getSku() {
        return sku;
    }

    public String getSizeLabel() {
        return sizeLabel;
    }

    public String getColor() {
        return color;
    }
}
