package com.wally.customersupport.catalog.application.service;

import java.math.BigDecimal;
import java.util.Objects;

import com.wally.customersupport.catalog.domain.model.CatalogProduct;
import com.wally.customersupport.catalog.domain.model.CatalogVariant;

/**
 * Minimal catalog fact exposed to the agent/application boundary.
 *
 * <p>It contains only fields that may be stated to a customer. It is not a
 * persistence entity and cannot contain SQL, prompt data, or channel data.</p>
 */
public record CatalogFact(
        String productName,
        String sku,
        String size,
        String color,
        BigDecimal price,
        String currency,
        int stock) {

    public CatalogFact {
        productName = required(productName, "productName");
        sku = required(sku, "sku");
        size = required(size, "size");
        color = required(color, "color");
        price = Objects.requireNonNull(price, "price");
        currency = required(currency, "currency");
        if (stock < 0) {
            throw new IllegalArgumentException("stock must not be negative");
        }
    }

    public static CatalogFact from(CatalogProduct product, CatalogVariant variant) {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(variant, "variant");
        return new CatalogFact(
                product.name(),
                variant.sku(),
                variant.size(),
                variant.color(),
                variant.price(),
                variant.currency(),
                variant.stock());
    }

    public boolean available() {
        return stock > 0;
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
