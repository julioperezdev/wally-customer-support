package com.wally.customersupport.order.infrastructure.payment;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import com.wally.customersupport.order.application.port.out.PaymentGateway;
import com.wally.customersupport.order.infrastructure.config.PaymentProperties;

/** Mercado Pago Checkout Pro adapter backed by the official Java SDK. */
public class MercadoPagoPaymentGateway implements PaymentGateway {

    private final PreferenceClient preferenceClient;
    private final PaymentClient paymentClient;
    private final MPRequestOptions requestOptions;

    public MercadoPagoPaymentGateway(PaymentProperties properties) {
        String accessToken = properties.effectiveMercadoPago().accessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Mercado Pago access token must be configured");
        }
        int timeoutMillis = timeoutMillis(properties.effectiveRequestTimeout());
        this.preferenceClient = new PreferenceClient();
        this.paymentClient = new PaymentClient();
        this.requestOptions = MPRequestOptions.builder()
                .accessToken(accessToken.trim())
                .connectionTimeout(timeoutMillis)
                .connectionRequestTimeout(timeoutMillis)
                .socketTimeout(timeoutMillis)
                .build();
    }

    @Override
    public PaymentPreference createPreference(CreatePreferenceRequest request) {
        List<PreferenceItemRequest> items = request.items().stream()
                .map(item -> PreferenceItemRequest.builder()
                        .id(item.sku())
                        .title(item.title())
                        .quantity(item.quantity())
                        .currencyId(item.currency())
                        .unitPrice(item.unitPrice())
                        .build())
                .toList();

        PreferenceRequest.PreferenceRequestBuilder builder = PreferenceRequest.builder()
                .items(items)
                .externalReference(request.orderId().toString());
        if (!request.notificationUrl().isBlank()) {
            builder.notificationUrl(request.notificationUrl());
        }

        try {
            Preference preference = preferenceClient.create(builder.build(), requestOptions);
            String preferenceId = required(preference.getId(), "preference id");
            String checkoutUrl = preference.getSandboxInitPoint();
            if (checkoutUrl == null || checkoutUrl.isBlank()) {
                checkoutUrl = preference.getInitPoint();
            }
            return new PaymentPreference("mercadopago", preferenceId, required(checkoutUrl, "checkout URL"));
        } catch (MPException | MPApiException exception) {
            throw new IllegalStateException("Mercado Pago preference request failed", exception);
        }
    }

    @Override
    public PaymentNotification getPayment(String providerPaymentId) {
        long paymentId;
        try {
            paymentId = Long.parseLong(providerPaymentId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Mercado Pago payment id must be numeric", exception);
        }

        try {
            Payment payment = paymentClient.get(paymentId, requestOptions);
            return new PaymentNotification(
                    required(payment.getId() == null ? null : payment.getId().toString(), "payment id"),
                    payment.getExternalReference(),
                    payment.getStatus(),
                    payment.getStatusDetail(),
                    firstInstant(payment.getDateApproved(), payment.getDateCreated()));
        } catch (MPException | MPApiException exception) {
            throw new IllegalStateException("Mercado Pago payment lookup failed", exception);
        }
    }

    private static int timeoutMillis(Duration timeout) {
        long millis = timeout.toMillis();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1, millis));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Mercado Pago returned no " + field);
        }
        return value;
    }

    private static java.time.Instant firstInstant(OffsetDateTime... values) {
        for (OffsetDateTime value : values) {
            if (value != null) return value.toInstant();
        }
        return null;
    }
}
