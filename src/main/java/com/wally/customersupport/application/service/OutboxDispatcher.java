package com.wally.customersupport.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.wally.customersupport.application.port.out.OutboxRepository;
import com.wally.customersupport.application.port.out.OutboundMessagePort;
import com.wally.customersupport.domain.model.OutboxMessage;
import com.wally.customersupport.domain.model.Channel;
import com.wally.customersupport.infrastructure.config.OutboxProperties;
import com.wally.customersupport.infrastructure.observability.StructuredEventLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxDispatcher.class);
    private static final int BATCH_SIZE = 50;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(30);

    private final OutboxRepository outboxRepository;
    private final Map<Channel, OutboundMessagePort> outboundMessagePorts;
    private final OutboxProperties properties;
    private final Clock clock;

    @Autowired
    public OutboxDispatcher(
            OutboxRepository outboxRepository,
            List<OutboundMessagePort> outboundMessagePorts,
            OutboxProperties properties) {
        this(outboxRepository, outboundMessagePorts, properties, Clock.systemUTC());
    }

    OutboxDispatcher(
            OutboxRepository outboxRepository,
            List<OutboundMessagePort> outboundMessagePorts,
            OutboxProperties properties,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.outboundMessagePorts = outboundMessagePorts.stream()
                .collect(Collectors.toUnmodifiableMap(OutboundMessagePort::channel, Function.identity()));
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${wcs.outbox.poll-interval-ms:5000}")
    public void dispatchDueMessages() {
        Instant now = clock.instant();
        List<OutboxMessage> dueMessages = outboxRepository.findDue(now, BATCH_SIZE);
        for (OutboxMessage outboxMessage : dueMessages) {
            dispatch(outboxMessage, now);
        }
    }

    private void dispatch(OutboxMessage outboxMessage, Instant now) {
        long startedAt = System.nanoTime();
        try {
            outboxRepository.markProcessing(outboxMessage.id());
            OutboundMessagePort outboundMessagePort = outboundMessagePorts.get(outboxMessage.message().channel());
            if (outboundMessagePort == null) {
                throw new IllegalStateException(
                        "No outbound adapter configured for channel " + outboxMessage.message().channel());
            }
            outboundMessagePort.send(outboxMessage.message());
            outboxRepository.markSent(outboxMessage.id(), clock.instant());
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("channel", outboxMessage.message().channel().name().toLowerCase(java.util.Locale.ROOT));
            fields.put("result", "SENT");
            fields.put("durationMs", elapsedMillis(startedAt));
            fields.put("correlationId", outboxMessage.message().conversationId());
            StructuredEventLog.info(LOGGER, "OUTBOUND_MESSAGE_DISPATCHED", fields);
        } catch (RuntimeException exception) {
            outboxRepository.markFailed(
                    outboxMessage.id(),
                    sanitizeError(exception),
                    now.plus(RETRY_DELAY),
                    outboxMessage.attempts() + 1 >= Math.max(1, properties.maxAttempts()));
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("channel", outboxMessage.message().channel().name().toLowerCase(java.util.Locale.ROOT));
            fields.put("result", "FAILED");
            fields.put("errorType", exception.getClass().getSimpleName());
            fields.put("durationMs", elapsedMillis(startedAt));
            fields.put("correlationId", outboxMessage.message().conversationId());
            StructuredEventLog.warn(LOGGER, "OUTBOUND_MESSAGE_DISPATCHED", fields);
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static String sanitizeError(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
