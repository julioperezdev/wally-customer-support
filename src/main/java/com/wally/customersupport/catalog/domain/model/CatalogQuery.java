package com.wally.customersupport.catalog.domain.model;

/**
 * Deterministic filters used to query the store catalog.
 *
 * <p>The LLM, when introduced, may extract these values, but it must not
 * generate SQL or catalog facts.</p>
 */
public record CatalogQuery(String name, String sku, String size, String color) {

    public CatalogQuery {
        name = normalize(name);
        sku = normalize(sku);
        size = normalize(size);
        color = normalize(color);
    }

    public boolean isEmpty() {
        return name == null && sku == null && size == null && color == null;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
