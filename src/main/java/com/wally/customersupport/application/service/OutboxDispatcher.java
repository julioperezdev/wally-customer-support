package com.wally.customersupport.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.wally.customersupport.application.port.out.OutboxRepository;
import com.wally.customersupport.application.port.out.OutboundMessagePort;
import com.wally.customersupport.domain.model.OutboxMessage;
import com.wally.customersupport.infrastructure.config.OutboxProperties;
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
    private final OutboundMessagePort outboundMessagePort;
    private final OutboxProperties properties;
    private final Clock clock;

    @Autowired
    public OutboxDispatcher(
            OutboxRepository outboxRepository,
            OutboundMessagePort outboundMessagePort,
            OutboxProperties properties) {
        this(outboxRepository, outboundMessagePort, properties, Clock.systemUTC());
    }

    OutboxDispatcher(
            OutboxRepository outboxRepository,
            OutboundMessagePort outboundMessagePort,
            OutboxProperties properties,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.outboundMessagePort = outboundMessagePort;
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
        try {
            outboxRepository.markProcessing(outboxMessage.id());
            outboundMessagePort.send(outboxMessage.message());
            outboxRepository.markSent(outboxMessage.id(), clock.instant());
        } catch (RuntimeException exception) {
            outboxRepository.markFailed(
                    outboxMessage.id(),
                    sanitizeError(exception),
                    now.plus(RETRY_DELAY),
                    outboxMessage.attempts() + 1 >= Math.max(1, properties.maxAttempts()));
            LOGGER.warn("Outbound message dispatch failed; retry policy applied");
        }
    }

    private static String sanitizeError(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
