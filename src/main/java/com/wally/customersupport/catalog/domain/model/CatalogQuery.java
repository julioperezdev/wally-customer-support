package com.wally.customersupport.catalog.domain.model;

/**
 * Deterministic filters used to query the store catalog.
 *
 * <p>The LLM, when introduced, may extract these values, but it must not
 * generate SQL or catalog facts.</p>
 */
public record CatalogQuery(String name, String sku, String size, String color, String productType) {

    public CatalogQuery(String name, String sku, String size, String color) {
        this(name, sku, size, color, null);
    }

    public CatalogQuery {
        name = normalize(name);
        sku = normalize(sku);
        size = normalize(size);
        color = normalize(color);
        productType = normalize(productType);
    }

    public boolean isEmpty() {
        return name == null && sku == null && size == null && color == null && productType == null;
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
                patch.productType() == null ? productType : patch.productType());
    }

    public CatalogQuery withoutProductType() {
        return new CatalogQuery(name, sku, size, color, null);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
