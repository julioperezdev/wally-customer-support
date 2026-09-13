package com.wally.customersupport.order.infrastructure.payment;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;

import com.wally.customersupport.order.infrastructure.config.PaymentProperties;
import org.springframework.stereotype.Component;

@Component
public class PaymentWebhookSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long MAX_TIMESTAMP_AGE_SECONDS = 900;

    private final PaymentProperties properties;
    private final Clock clock;

    public PaymentWebhookSignatureVerifier(PaymentProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean verify(String signatureHeader, String requestId, String dataId) {
        PaymentProperties.Webhook webhook = properties.effectiveWebhook();
        if (!webhook.signatureRequired() && webhook.effectiveSecret().isBlank()) {
            return true;
        }
        if (signatureHeader == null || requestId == null || dataId == null
                || webhook.effectiveSecret().isBlank()) {
            return false;
        }
        Map<String, String> values = parse(signatureHeader);
        String timestamp = values.get("ts");
        String receivedSignature = values.get("v1");
        if (timestamp == null || receivedSignature == null || dataId == null || dataId.isBlank()) {
            return false;
        }
        long timestampValue;
        try {
            timestampValue = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            return false;
        }
        long age = Math.abs(Instant.now(clock).getEpochSecond() - timestampValue);
        if (age > MAX_TIMESTAMP_AGE_SECONDS) {
            return false;
        }
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
        String expected = sign(manifest, webhook.effectiveSecret());
        return java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                receivedSignature.getBytes(StandardCharsets.US_ASCII));
    }

    private static Map<String, String> parse(String header) {
        Map<String, String> values = new java.util.HashMap<>();
        for (String entry : header.split(",")) {
            String[] pair = entry.trim().split("=", 2);
            if (pair.length == 2) values.put(pair[0], pair[1]);
        }
        return values;
    }

    private static String sign(String value, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to verify payment webhook signature", exception);
        }
    }
}
