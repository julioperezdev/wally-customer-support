package com.wally.customersupport.catalog.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record CatalogVariant(
        UUID id,
        String sku,
        String size,
        String color,
        BigDecimal price,
        String currency,
        int stock,
        boolean active,
        String imageObjectKey) {

    public CatalogVariant(
            UUID id,
            String sku,
            String size,
            String color,
            BigDecimal price,
            String currency,
            int stock,
            boolean active) {
        this(id, sku, size, color, price, currency, stock, active, null);
    }
}
