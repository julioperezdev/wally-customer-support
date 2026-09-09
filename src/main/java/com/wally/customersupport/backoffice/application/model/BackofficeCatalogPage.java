package com.wally.customersupport.backoffice.application.model;

import java.util.List;

public record BackofficeCatalogPage(
        List<BackofficeCatalogProduct> items,
        int page,
        int size,
        boolean hasNext) {

    public BackofficeCatalogPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
