package com.wally.customersupport.backoffice.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BackofficeOrder(
        UUID id,
        String customerReference,
        String status,
        String currency,
        BigDecimal total,
        String paymentProvider,
        String paymentPreferenceId,
        String paymentUrl,
        String externalPaymentId,
        Instant createdAt,
        Instant updatedAt,
        List<Item> items) {

    public BackofficeOrder {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Item(
            String sku,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            String currency,
            BigDecimal lineTotal) {
    }
}
