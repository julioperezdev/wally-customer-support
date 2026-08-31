package com.wally.customersupport.adapter.in.web.whatsapp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class HmacVerifierTest {

    private final HmacVerifier verifier = new HmacVerifier();

    @Test
    void acceptsSignatureCalculatedFromOriginalBody() throws Exception {
        String body = "{\"synthetic\":true}";
        String secret = "synthetic-app-secret";

        assertTrue(verifier.isValid(body, signature(body, secret), secret));
    }

    @Test
    void rejectsMissingOrInvalidSignature() {
        assertFalse(verifier.isValid("body", null, "secret"));
        assertFalse(verifier.isValid("body", "sha256=00", "secret"));
        assertFalse(verifier.isValid("body", "sha256=00", ""));
    }

    private static String signature(String body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
