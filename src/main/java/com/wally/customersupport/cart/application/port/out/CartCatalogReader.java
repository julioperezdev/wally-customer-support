package com.wally.customersupport.cart.application.port.out;

import java.math.BigDecimal;
import java.util.Optional;

public interface CartCatalogReader {

    Optional<CatalogItem> findBySku(String sku);

    record CatalogItem(
            String sku,
            String productName,
            String size,
            String color,
            BigDecimal unitPrice,
            String currency,
            int stock,
            boolean active) {
    }
}
