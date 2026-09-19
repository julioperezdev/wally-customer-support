package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.math.BigDecimal;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;

/**
 * Persistence-only representation of a catalog selection.
 *
 * <p>It deliberately contains only record components. Domain convenience
 * methods such as {@code isEmpty()} must not become part of the JSON contract
 * stored in PostgreSQL.</p>
 */
public record CatalogQueryJson(
        String name,
        String sku,
        String size,
        String color,
        String productType,
        BigDecimal minPrice,
        BigDecimal maxPrice) {

    static CatalogQueryJson fromDomain(CatalogQuery query) {
        if (query == null) {
            return new CatalogQueryJson(null, null, null, null, null, null, null);
        }
        return new CatalogQueryJson(
                query.name(),
                query.sku(),
                query.size(),
                query.color(),
                query.productType(),
                query.minPrice(),
                query.maxPrice());
    }

    CatalogQuery toDomain() {
        return new CatalogQuery(name, sku, size, color, productType, minPrice, maxPrice);
    }
}
