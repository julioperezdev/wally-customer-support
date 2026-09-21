package com.wally.customersupport.catalog.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Returns only the names of the filters present in this query. The values
     * are intentionally excluded so this shape can be emitted in telemetry
     * without logging product names, SKUs or customer text.
     */
    public List<String> presentFieldNames() {
        List<String> fields = new ArrayList<>();
        if (name != null) {
            fields.add("name");
        }
        if (sku != null) {
            fields.add("sku");
        }
        if (size != null) {
            fields.add("size");
        }
        if (color != null) {
            fields.add("color");
        }
        if (productType != null) {
            fields.add("productType");
        }
        if (minPrice != null) {
            fields.add("minPrice");
        }
        if (maxPrice != null) {
            fields.add("maxPrice");
        }
        return List.copyOf(fields);
    }

    public int presentFieldCount() {
        return presentFieldNames().size();
    }

    public boolean hasPrimarySelector() {
        return name != null || sku != null || productType != null;
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

    /**
     * Completes this query only with values that are not already present.
     * This is used while walking history backwards: the latest turn wins and
     * older turns may only provide missing context.
     */
    public CatalogQuery mergeMissing(CatalogQuery fallback) {
        if (fallback == null) {
            return this;
        }
        return new CatalogQuery(
                name == null ? fallback.name() : name,
                sku == null ? fallback.sku() : sku,
                size == null ? fallback.size() : size,
                color == null ? fallback.color() : color,
                productType == null ? fallback.productType() : productType,
                minPrice == null ? fallback.minPrice() : minPrice,
                maxPrice == null ? fallback.maxPrice() : maxPrice);
    }

    public CatalogQuery withoutProductType() {
        return new CatalogQuery(name, sku, size, color, null, minPrice, maxPrice);
    }

    public CatalogQuery withoutSize() {
        return new CatalogQuery(name, sku, null, color, productType, minPrice, maxPrice);
    }

    public CatalogQuery withoutColor() {
        return new CatalogQuery(name, sku, size, null, productType, minPrice, maxPrice);
    }

    public CatalogQuery withoutPriceRange() {
        return new CatalogQuery(name, sku, size, color, productType, null, null);
    }

    public CatalogQuery withoutSizeAndColor() {
        return new CatalogQuery(name, sku, null, null, productType, minPrice, maxPrice);
    }

    public CatalogQuery withProductType(String replacementProductType) {
        return new CatalogQuery(name, sku, size, color, replacementProductType, minPrice, maxPrice);
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
