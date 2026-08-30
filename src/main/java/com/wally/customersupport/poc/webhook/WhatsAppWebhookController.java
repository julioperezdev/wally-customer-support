package com.wally.customersupport.poc.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import com.wally.customersupport.poc.config.WhatsAppPocProperties;
import com.wally.customersupport.poc.whatsapp.MetaWhatsAppClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook/whatsapp")
public class WhatsAppWebhookController {

    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private final WhatsAppPocProperties properties;
    private final HmacVerifier hmacVerifier;
    private final WhatsAppMessageProcessor messageProcessor;

    public WhatsAppWebhookController(
            WhatsAppPocProperties properties,
            HmacVerifier hmacVerifier,
            WhatsAppMessageProcessor messageProcessor) {
        this.properties = properties;
        this.hmacVerifier = hmacVerifier;
        this.messageProcessor = messageProcessor;
    }

    @GetMapping
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if (!"subscribe".equals(mode)
                || isBlank(verifyToken)
                || isBlank(properties.verifyToken())
                || isBlank(challenge)
                || !constantTimeEquals(verifyToken, properties.verifyToken())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(challenge);
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody String rawBody,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
        if (!hmacVerifier.isValid(rawBody, signature, properties.appSecret())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            messageProcessor.process(rawBody);
            return ResponseEntity.ok().build();
        } catch (PocPayloadException exception) {
            LOGGER.warn("PoC webhook rejected malformed JSON payload");
            return ResponseEntity.badRequest().build();
        } catch (MetaWhatsAppClientException exception) {
            LOGGER.warn("PoC outbound Meta request failed with status {}", exception.statusCode());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }

    private static boolean constantTimeEquals(String first, String second) {
        if (isBlank(first) || isBlank(second)) {
            return false;
        }
        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8),
                second.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
