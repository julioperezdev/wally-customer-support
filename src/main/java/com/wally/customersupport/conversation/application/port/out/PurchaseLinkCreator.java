package com.wally.customersupport.conversation.application.port.out;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates a customer-facing checkout link without exposing an order or
 * payment-provider implementation to the conversation workflow.
 */
@FunctionalInterface
public interface PurchaseLinkCreator {

    Optional<PurchaseLink> create(CreatePurchaseLinkRequest request);

    record CreatePurchaseLinkRequest(
            UUID conversationId,
            String customerReference,
            String sku,
            int quantity,
            String idempotencyKey) {
    }

    record PurchaseLink(
            UUID orderId,
            String productName,
            String sku,
            int quantity,
            BigDecimal total,
            String currency,
            String provider,
            String checkoutUrl) {
    }
}
