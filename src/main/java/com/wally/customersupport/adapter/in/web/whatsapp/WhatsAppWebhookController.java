package com.wally.customersupport.adapter.in.web.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import com.wally.customersupport.application.port.in.InboundMessagePort;
import com.wally.customersupport.domain.model.InboundMessageCommand;
import com.wally.customersupport.domain.model.InboundMessageResult;
import com.wally.customersupport.infrastructure.config.WhatsAppProperties;
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

    private final WhatsAppProperties properties;
    private final HmacVerifier hmacVerifier;
    private final WhatsAppInboundPayloadParser payloadParser;
    private final InboundMessagePort inboundMessagePort;

    public WhatsAppWebhookController(
            WhatsAppProperties properties,
            HmacVerifier hmacVerifier,
            WhatsAppInboundPayloadParser payloadParser,
            InboundMessagePort inboundMessagePort) {
        this.properties = properties;
        this.hmacVerifier = hmacVerifier;
        this.payloadParser = payloadParser;
        this.inboundMessagePort = inboundMessagePort;
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
            LOGGER.warn("WCS_EVENT eventType=WEBHOOK_REJECTED channel=whatsapp reason=invalid_signature");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<InboundMessageCommand> commands = payloadParser.parse(rawBody);
            LOGGER.info("WCS_EVENT eventType=WEBHOOK_ACCEPTED channel=whatsapp commandCount={}", commands.size());
            for (InboundMessageCommand command : commands) {
                long startedAt = System.nanoTime();
                InboundMessageResult result = inboundMessagePort.accept(command);
                LOGGER.info(
                        "WCS_EVENT eventType=INBOUND_MESSAGE_PROCESSED channel={} result={} durationMs={}",
                        command.channel(),
                        result.result(),
                        elapsedMillis(startedAt));
            }
            return ResponseEntity.ok().build();
        } catch (WhatsAppPayloadException exception) {
            LOGGER.warn("WCS_EVENT eventType=WEBHOOK_REJECTED channel=whatsapp reason=malformed_payload");
            return ResponseEntity.badRequest().build();
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
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
