package com.wally.customersupport.conversation.domain.model;

import java.util.Objects;

/**
 * Safe, bounded reference to a catalog candidate shown during the conversation.
 *
 * <p>This is navigation context only. Price and stock deliberately do not
 * belong here because PostgreSQL remains authoritative for every cart and
 * purchase operation.</p>
 */
public record CatalogCandidateReference(
        String productName,
        String sku,
        String size,
        String color) {

    public CatalogCandidateReference {
        productName = required(productName, "productName");
        sku = required(sku, "sku");
        size = normalize(size);
        color = normalize(color);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
