package com.wally.customersupport.conversation.application.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.out.ProcessingAttemptRepository;
import com.wally.customersupport.conversation.domain.model.ProcessingAttempt;
import com.wally.customersupport.conversation.domain.model.ProcessingAttemptStatus;
import com.wally.customersupport.shared.infrastructure.config.InboundProcessingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InboundProcessingWorkerTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    @Mock
    private ProcessingAttemptRepository processingAttemptRepository;
    @Mock
    private InboundMessageProcessingService processingService;

    @Test
    void claimsAndProcessesDueInboundMessages() {
        ProcessingAttempt attempt = attempt(0);
        when(processingAttemptRepository.findDue(NOW, 20, Duration.ofMinutes(5)))
                .thenReturn(List.of(attempt));
        when(processingAttemptRepository.claim(attempt.id(), NOW)).thenReturn(true);

        worker(3).processDueMessages();

        verify(processingAttemptRepository).claim(attempt.id(), NOW);
        verify(processingService).process(attempt);
        verify(processingService, org.mockito.Mockito.never()).fail(any(), any(), any(Boolean.class));
    }

    @Test
    void movesTransientFailuresToRetry() {
        ProcessingAttempt attempt = attempt(1);
        when(processingAttemptRepository.findDue(NOW, 20, Duration.ofMinutes(5)))
                .thenReturn(List.of(attempt));
        when(processingAttemptRepository.claim(attempt.id(), NOW)).thenReturn(true);
        doThrow(new IllegalStateException("synthetic failure"))
                .when(processingService).process(attempt);

        worker(3).processDueMessages();

        verify(processingService).fail(attempt, "IllegalStateException", false);
    }

    @Test
    void marksFailureAsExhaustedOnLastAttempt() {
        ProcessingAttempt attempt = attempt(2);
        when(processingAttemptRepository.findDue(NOW, 20, Duration.ofMinutes(5)))
                .thenReturn(List.of(attempt));
        when(processingAttemptRepository.claim(attempt.id(), NOW)).thenReturn(true);
        doThrow(new IllegalStateException("synthetic failure"))
                .when(processingService).process(attempt);

        worker(3).processDueMessages();

        verify(processingService).fail(attempt, "IllegalStateException", true);
    }

    private InboundProcessingWorker worker(int maxAttempts) {
        return new InboundProcessingWorker(
                processingAttemptRepository,
                processingService,
                new InboundProcessingProperties(1000, 20, maxAttempts, Duration.ofMinutes(5), Duration.ofSeconds(30)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ProcessingAttempt attempt(int attemptCount) {
        UUID id = UUID.randomUUID();
        return new ProcessingAttempt(
                id,
                UUID.randomUUID(),
                ProcessingAttemptStatus.PENDING,
                attemptCount,
                null,
                NOW,
                NOW,
                NOW,
                null);
    }
}
