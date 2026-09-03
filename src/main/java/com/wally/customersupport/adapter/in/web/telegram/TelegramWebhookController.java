package com.wally.customersupport.adapter.in.web.telegram;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import com.wally.customersupport.application.port.in.InboundMessagePort;
import com.wally.customersupport.domain.model.InboundMessageCommand;
import com.wally.customersupport.infrastructure.config.TelegramProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/webhook/telegram")
@ConditionalOnProperty(name = "wcs.telegram.enabled", havingValue = "true")
public class TelegramWebhookController {

    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramProperties properties;
    private final TelegramInboundPayloadParser payloadParser;
    private final InboundMessagePort inboundMessagePort;

    public TelegramWebhookController(
            TelegramProperties properties,
            TelegramInboundPayloadParser payloadParser,
            InboundMessagePort inboundMessagePort) {
        this.properties = properties;
        this.payloadParser = payloadParser;
        this.inboundMessagePort = inboundMessagePort;
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody String rawBody,
            @RequestHeader(name = SECRET_HEADER, required = false) String secretToken) {
        if (!constantTimeEquals(secretToken, properties.webhookSecretToken())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<InboundMessageCommand> commands = payloadParser.parse(rawBody);
            commands.forEach(inboundMessagePort::accept);
            return ResponseEntity.ok().build();
        } catch (TelegramPayloadException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    private static boolean constantTimeEquals(String first, String second) {
        if (first == null || first.isBlank() || second == null || second.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.UTF_8),
                second.getBytes(StandardCharsets.UTF_8));
    }
}
