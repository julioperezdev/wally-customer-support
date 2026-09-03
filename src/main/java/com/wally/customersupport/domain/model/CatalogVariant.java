package com.wally.customersupport.domain.model;

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
        boolean active) {
}
