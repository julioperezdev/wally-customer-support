package com.wally.customersupport.catalog.application.service;

import java.util.Objects;

/** Opaque catalog media reference; it is not a public URL. */
public record CatalogImage(String sku, String reference) {

    public CatalogImage {
        sku = required(sku, "sku");
        reference = required(reference, "reference");
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
