package com.wally.customersupport.order.infrastructure.payment;

import java.time.Instant;

import com.wally.customersupport.order.application.port.out.PaymentGateway;
import com.wally.customersupport.order.infrastructure.config.PaymentProperties;

public class MockPaymentGateway implements PaymentGateway {

    private final PaymentProperties properties;

    public MockPaymentGateway(PaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentPreference createPreference(CreatePreferenceRequest request) {
        String id = "mock-preference-" + request.orderId();
        return new PaymentPreference(
                "mock",
                id,
                "https://sandbox.example.invalid/pay/" + request.orderId());
    }

    @Override
    public PaymentNotification getPayment(String providerPaymentId) {
        return new PaymentNotification(
                providerPaymentId,
                providerPaymentId.startsWith("mock-payment-")
                        ? providerPaymentId.substring("mock-payment-".length())
                        : null,
                "pending",
                "mock_pending",
                Instant.now());
    }
}
