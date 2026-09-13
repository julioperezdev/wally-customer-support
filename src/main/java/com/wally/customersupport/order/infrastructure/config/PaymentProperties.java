package com.wally.customersupport.order.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.payment")
public record PaymentProperties(
        String provider,
        String currency,
        String notificationUrl,
        Duration requestTimeout,
        MercadoPago mercadoPago,
        Webhook webhook) {

    public String effectiveProvider() {
        return provider == null || provider.isBlank() ? "mock" : provider.trim().toLowerCase();
    }

    public String effectiveCurrency() {
        return currency == null || currency.isBlank() ? "ARS" : currency.trim().toUpperCase();
    }

    public String effectiveNotificationUrl() {
        return notificationUrl == null ? "" : notificationUrl.trim();
    }

    public Duration effectiveRequestTimeout() {
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()
                || requestTimeout.compareTo(Duration.ofSeconds(60)) > 0) {
            return Duration.ofSeconds(10);
        }
        return requestTimeout;
    }

    public MercadoPago effectiveMercadoPago() {
        return mercadoPago == null ? new MercadoPago(null) : mercadoPago;
    }

    public Webhook effectiveWebhook() {
        return webhook == null ? new Webhook(true, null) : webhook;
    }

    public record MercadoPago(String accessToken) {
    }

    public record Webhook(boolean signatureRequired, String secret) {

        public String effectiveSecret() {
            return secret == null ? "" : secret.trim();
        }
    }
}
