package com.wally.customersupport.catalog.domain.model;

import java.util.List;
import java.util.UUID;

public record CatalogProduct(
        UUID id,
        String name,
        String description,
        String imageObjectKey,
        boolean active,
        boolean demo,
        List<CatalogVariant> variants) {

    public CatalogProduct {
        variants = variants == null ? List.of() : List.copyOf(variants);
    }
}
