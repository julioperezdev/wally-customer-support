package com.wally.customersupport.adapter.in.web.telegram;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wally.customersupport.application.port.in.InboundMessagePort;
import com.wally.customersupport.domain.model.InboundMessageCommand;
import com.wally.customersupport.domain.model.InboundMessageResult;
import com.wally.customersupport.infrastructure.config.TelegramProperties;
import com.wally.customersupport.infrastructure.observability.StructuredEventLog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramWebhookController.class);

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
            StructuredEventLog.warn(LOGGER, "WEBHOOK_REJECTED", Map.of(
                    "channel", "telegram",
                    "reason", "invalid_secret"));
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            List<InboundMessageCommand> commands = payloadParser.parse(rawBody);
            StructuredEventLog.info(LOGGER, "WEBHOOK_ACCEPTED", Map.of(
                    "channel", "telegram",
                    "commandCount", commands.size()));
            for (InboundMessageCommand command : commands) {
                long startedAt = System.nanoTime();
                InboundMessageResult result = inboundMessagePort.accept(command);
                Map<String, Object> fields = new LinkedHashMap<>();
                fields.put("channel", command.channel().name().toLowerCase(java.util.Locale.ROOT));
                fields.put("result", result.result().name());
                fields.put("durationMs", elapsedMillis(startedAt));
                StructuredEventLog.info(LOGGER, "INBOUND_MESSAGE_PROCESSED", fields);
            }
            return ResponseEntity.ok().build();
        } catch (TelegramPayloadException exception) {
            StructuredEventLog.warn(LOGGER, "WEBHOOK_REJECTED", Map.of(
                    "channel", "telegram",
                    "reason", "malformed_payload"));
            return ResponseEntity.badRequest().build();
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
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
