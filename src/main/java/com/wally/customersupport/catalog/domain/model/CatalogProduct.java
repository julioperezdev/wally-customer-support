package com.wally.customersupport.catalog.domain.model;

import java.util.List;
import java.util.UUID;

public record CatalogProduct(
        UUID id,
        String name,
        String description,
        String imageObjectKey,
        boolean active,
        boolean demo,
        List<CatalogVariant> variants,
        String productType) {

    public CatalogProduct(
            UUID id,
            String name,
            String description,
            String imageObjectKey,
            boolean active,
            boolean demo,
            List<CatalogVariant> variants) {
        this(id, name, description, imageObjectKey, active, demo, variants, null);
    }

    public CatalogProduct {
        variants = variants == null ? List.of() : List.copyOf(variants);
        productType = productType == null || productType.isBlank() ? null : productType.trim();
    }
}
