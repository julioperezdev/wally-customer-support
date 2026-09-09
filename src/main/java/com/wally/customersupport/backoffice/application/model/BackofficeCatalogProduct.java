package com.wally.customersupport.backoffice.application.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record BackofficeCatalogProduct(
        UUID id,
        String name,
        String description,
        String productType,
        String imageObjectKey,
        boolean active,
        boolean demo,
        List<BackofficeCatalogVariant> variants) {

    public BackofficeCatalogProduct {
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    public record BackofficeCatalogVariant(
            UUID id,
            String sku,
            String size,
            String color,
            BigDecimal price,
            String currency,
            int stock,
            boolean active) {
    }
}
