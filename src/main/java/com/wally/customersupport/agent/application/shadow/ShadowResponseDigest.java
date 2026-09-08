package com.wally.customersupport.agent.application.shadow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Creates a non-reversible, in-memory-only fingerprint for quality comparison. */
public final class ShadowResponseDigest {

    private ShadowResponseDigest() {
    }

    public static String sha256(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalize(response).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private static String normalize(String value) {
        return value.strip().replace("\r\n", "\n").replace('\r', '\n');
    }
}
