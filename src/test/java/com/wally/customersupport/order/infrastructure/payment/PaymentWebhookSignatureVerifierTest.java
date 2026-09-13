package com.wally.customersupport.order.infrastructure.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.wally.customersupport.order.infrastructure.config.PaymentProperties;
import org.junit.jupiter.api.Test;

class PaymentWebhookSignatureVerifierTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");

    @Test
    void acceptsMercadoPagoManifestWithFreshSignature() throws Exception {
        String timestamp = String.valueOf(NOW.getEpochSecond());
        String signature = signature("id:payment-1;request-id:request-1;ts:" + timestamp + ";", "secret");
        var verifier = new PaymentWebhookSignatureVerifier(properties(true, "secret"), fixedClock());

        assertTrue(verifier.verify("ts=" + timestamp + ",v1=" + signature,
                "request-1", "payment-1"));
    }

    @Test
    void rejectsInvalidOrExpiredSignatures() throws Exception {
        String timestamp = String.valueOf(NOW.minusSeconds(901).getEpochSecond());
        String signature = signature("id:payment-1;request-id:request-1;ts:" + timestamp + ";", "secret");
        var verifier = new PaymentWebhookSignatureVerifier(properties(true, "secret"), fixedClock());

        assertFalse(verifier.verify("ts=" + timestamp + ",v1=" + signature,
                "request-1", "payment-1"));
        assertFalse(verifier.verify("ts=" + NOW.getEpochSecond() + ",v1=wrong",
                "request-1", "payment-1"));
    }

    @Test
    void allowsUnsignedEventsOnlyWhenExplicitlyConfigured() {
        var verifier = new PaymentWebhookSignatureVerifier(properties(false, null), fixedClock());

        assertTrue(verifier.verify(null, null, null));
    }

    private static PaymentProperties properties(boolean required, String secret) {
        return new PaymentProperties("mercadopago", "ARS", "", Duration.ofSeconds(5),
                new PaymentProperties.MercadoPago("token"),
                new PaymentProperties.Webhook(required, secret));
    }

    private static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static String signature(String value, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
