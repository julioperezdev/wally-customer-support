package com.wally.customersupport.poc.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

@Component
public class HmacVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    public boolean isValid(String rawBody, String signatureHeader, String appSecret) {
        if (isBlank(rawBody) || isBlank(signatureHeader) || isBlank(appSecret)
                || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            byte[] received = HexFormat.of().parseHex(signatureHeader.substring(SIGNATURE_PREFIX.length()));
            return MessageDigest.isEqual(digest, received);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
