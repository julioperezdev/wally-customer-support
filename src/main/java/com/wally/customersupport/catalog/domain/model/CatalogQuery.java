package com.wally.customersupport.catalog.domain.model;

import java.math.BigDecimal;

/**
 * Deterministic filters used to query the store catalog.
 *
 * <p>The LLM, when introduced, may extract these values, but it must not
 * generate SQL or catalog facts.</p>
 */
public record CatalogQuery(
        String name,
        String sku,
        String size,
        String color,
        String productType,
        BigDecimal minPrice,
        BigDecimal maxPrice) {

    public CatalogQuery(String name, String sku, String size, String color) {
        this(name, sku, size, color, null, null, null);
    }

    public CatalogQuery(String name, String sku, String size, String color, String productType) {
        this(name, sku, size, color, productType, null, null);
    }

    public CatalogQuery {
        name = normalize(name);
        sku = normalize(sku);
        size = normalize(size);
        color = normalize(color);
        productType = normalize(productType);
        minPrice = normalizePrice(minPrice, "minPrice");
        maxPrice = normalizePrice(maxPrice, "maxPrice");
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException("minPrice must not be greater than maxPrice");
        }
    }

    public static CatalogQuery empty() {
        return new CatalogQuery(null, null, null, null);
    }

    public boolean isEmpty() {
        return name == null && sku == null && size == null && color == null && productType == null
                && minPrice == null && maxPrice == null;
    }

    public CatalogQuery merge(CatalogQuery patch) {
        if (patch == null) {
            return this;
        }
        return new CatalogQuery(
                patch.name() == null ? name : patch.name(),
                patch.sku() == null ? sku : patch.sku(),
                patch.size() == null ? size : patch.size(),
                patch.color() == null ? color : patch.color(),
                patch.productType() == null ? productType : patch.productType(),
                patch.minPrice() == null ? minPrice : patch.minPrice(),
                patch.maxPrice() == null ? maxPrice : patch.maxPrice());
    }

    public CatalogQuery withoutProductType() {
        return new CatalogQuery(name, sku, size, color, null, minPrice, maxPrice);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static BigDecimal normalizePrice(BigDecimal value, String field) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }
}
