package com.wally.customersupport.conversation.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.wally.customersupport.conversation.application.port.out.ConversationRetentionRepository;
import com.wally.customersupport.shared.infrastructure.config.ConversationRetentionProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationRetentionCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

    @Mock
    private ConversationRetentionRepository repository;

    @Test
    void doesNothingWhenRetentionIsDisabled() {
        ConversationRetentionProperties properties = new ConversationRetentionProperties(
                false, Duration.ofDays(30), Duration.ofDays(90), Duration.ofDays(365), 10, 1000);
        ConversationRetentionCleanupService service = new ConversationRetentionCleanupService(
                repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        ConversationRetentionCleanupService.CleanupResult result = service.run();

        assertFalse(result.executed());
        verify(repository, org.mockito.Mockito.never())
                .redactMessageBodiesBefore(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(), eq(10));
    }

    @Test
    void redactsContentBeforeDeletingMetadataAndLeavesAggregateMetricsUntouched() {
        ConversationRetentionProperties properties = new ConversationRetentionProperties(
                true, Duration.ofDays(30), Duration.ofDays(90), Duration.ofDays(365), 10, 1000);
        ConversationRetentionCleanupService service = new ConversationRetentionCleanupService(
                repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.redactMessageBodiesBefore(
                eq(NOW.minus(Duration.ofDays(30))), eq("[REDACTED_AFTER_RETENTION]"), eq(10)))
                .thenReturn(3);
        when(repository.deleteMessagesBefore(eq(NOW.minus(Duration.ofDays(90))), eq(10)))
                .thenReturn(2);

        ConversationRetentionCleanupService.CleanupResult result = service.run();

        assertEquals(3, result.contentRedacted());
        assertEquals(2, result.metadataDeleted());
        verify(repository).redactMessageBodiesBefore(
                NOW.minus(Duration.ofDays(30)), "[REDACTED_AFTER_RETENTION]", 10);
        verify(repository).deleteMessagesBefore(NOW.minus(Duration.ofDays(90)), 10);
    }
}
