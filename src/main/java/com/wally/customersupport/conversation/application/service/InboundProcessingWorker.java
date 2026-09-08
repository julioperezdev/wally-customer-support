package com.wally.customersupport.conversation.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wally.customersupport.conversation.application.port.out.ProcessingAttemptRepository;
import com.wally.customersupport.conversation.domain.model.ProcessingAttempt;
import com.wally.customersupport.shared.infrastructure.config.InboundProcessingProperties;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class InboundProcessingWorker {

    private final ProcessingAttemptRepository processingAttemptRepository;
    private final InboundMessageProcessingService processingService;
    private final InboundProcessingProperties properties;
    private final Clock clock;

    @Autowired
    public InboundProcessingWorker(
            ProcessingAttemptRepository processingAttemptRepository,
            InboundMessageProcessingService processingService,
            InboundProcessingProperties properties) {
        this(processingAttemptRepository, processingService, properties, Clock.systemUTC());
    }

    InboundProcessingWorker(
            ProcessingAttemptRepository processingAttemptRepository,
            InboundMessageProcessingService processingService,
            InboundProcessingProperties properties,
            Clock clock) {
        this.processingAttemptRepository = processingAttemptRepository;
        this.processingService = processingService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${wcs.inbound.poll-interval-ms:1000}")
    public void processDueMessages() {
        Instant now = clock.instant();
        List<ProcessingAttempt> dueAttempts = processingAttemptRepository.findDue(
                now,
                Math.max(1, properties.batchSize()),
                properties.leaseDuration());
        for (ProcessingAttempt attempt : dueAttempts) {
            if (!processingAttemptRepository.claim(attempt.id(), now)) {
                continue;
            }
            long startedAt = System.nanoTime();
            try {
                processingService.process(attempt);
                logEvent("INBOUND_MESSAGE_PROCESSED", attempt, "COMPLETED", null, startedAt);
            } catch (RuntimeException exception) {
                boolean exhausted = attempt.attemptCount() + 1 >= Math.max(1, properties.maxAttempts());
                processingService.fail(attempt, exception.getClass().getSimpleName(), exhausted);
                logEvent(
                        exhausted ? "INBOUND_MESSAGE_FAILED" : "INBOUND_MESSAGE_RETRY_SCHEDULED",
                        attempt,
                        exhausted ? "FAILED" : "RETRYING",
                        exception.getClass().getSimpleName(),
                        startedAt);
            }
        }
    }

    private void logEvent(
            String event,
            ProcessingAttempt attempt,
            String result,
            String errorType,
            long startedAt) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("result", result);
        fields.put("attempt", attempt.attemptCount() + 1);
        fields.put("durationMs", elapsedMillis(startedAt));
        fields.put("correlationId", attempt.messageId());
        if (errorType != null) {
            fields.put("errorType", errorType);
        }
        StructuredEventLog.info(log, event, fields);
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
