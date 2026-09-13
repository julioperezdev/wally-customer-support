package com.wally.customersupport.order.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PaymentGateway {

    PaymentPreference createPreference(CreatePreferenceRequest request);

    PaymentNotification getPayment(String providerPaymentId);

    record CreatePreferenceRequest(
            UUID orderId,
            String currency,
            String notificationUrl,
            List<Item> items) {

        public CreatePreferenceRequest {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record Item(
            String sku,
            String title,
            int quantity,
            BigDecimal unitPrice,
            String currency) {
    }

    record PaymentPreference(
            String provider,
            String preferenceId,
            String checkoutUrl) {
    }

    record PaymentNotification(
            String providerPaymentId,
            String externalReference,
            String status,
            String statusDetail,
            Instant occurredAt) {
    }
}
