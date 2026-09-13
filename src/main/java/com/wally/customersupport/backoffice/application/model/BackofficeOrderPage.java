package com.wally.customersupport.backoffice.application.model;

import java.util.List;

public record BackofficeOrderPage(
        List<BackofficeOrder> items,
        int page,
        int size,
        boolean hasNext) {

    public BackofficeOrderPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
