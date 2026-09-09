package com.wally.customersupport.backoffice.application.model;

import java.time.Instant;
import java.util.UUID;

public record BackofficeStockAdjustment(
        UUID id,
        String sku,
        int previousStock,
        int delta,
        int newStock,
        String reason,
        String actorKey,
        String idempotencyKey,
        Instant createdAt) {
}
