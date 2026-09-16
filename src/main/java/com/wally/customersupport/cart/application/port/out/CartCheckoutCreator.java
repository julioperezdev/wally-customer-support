package com.wally.customersupport.cart.application.port.out;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartCheckoutCreator {

    Optional<CartCheckout> create(CreateCartCheckoutRequest request);

    void cancelActive(UUID cartId);

    record CreateCartCheckoutRequest(
            UUID conversationId,
            String customerReference,
            UUID cartId,
            long cartVersion,
            List<Item> items) {

        public CreateCartCheckoutRequest {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record Item(String sku, int quantity) {
    }

    record CartCheckout(
            UUID orderId,
            BigDecimal total,
            String currency,
            String provider,
            String checkoutUrl) {
    }
}
