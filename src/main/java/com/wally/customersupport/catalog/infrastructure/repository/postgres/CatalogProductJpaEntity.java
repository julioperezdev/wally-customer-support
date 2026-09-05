package com.wally.customersupport.catalog.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.catalog.domain.model.CatalogProduct;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "catalog_products", schema = "wcs")
public class CatalogProductJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "image_object_key", length = 512)
    private String imageObjectKey;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean demo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<CatalogVariantJpaEntity> variants = new ArrayList<>();

    protected CatalogProductJpaEntity() {
    }

    public CatalogProduct toDomain(CatalogQuery query) {
        List<com.wally.customersupport.catalog.domain.model.CatalogVariant> matchingVariants = variants.stream()
                .filter(CatalogVariantJpaEntity::isActive)
                .filter(variant -> matches(variant, query))
                .map(CatalogVariantJpaEntity::toDomain)
                .toList();

        return new CatalogProduct(id, name, description, imageObjectKey, active, demo, matchingVariants);
    }

    private static boolean matches(CatalogVariantJpaEntity variant, CatalogQuery query) {
        return query == null
                || (query.sku() == null || query.sku().equalsIgnoreCase(variant.getSku()))
                && (query.size() == null || query.size().equalsIgnoreCase(variant.getSizeLabel()))
                && (query.color() == null || query.color().equalsIgnoreCase(variant.getColor()));
    }

    public String getName() {
        return name;
    }
}
