package com.wally.customersupport.conversation.application.service;

import java.time.Clock;
import java.time.Instant;

import com.wally.customersupport.conversation.application.port.out.ConversationRetentionRepository;
import com.wally.customersupport.shared.infrastructure.config.ConversationRetentionProperties;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationRetentionCleanupService {

    private static final String REDACTED_BODY = "[REDACTED_AFTER_RETENTION]";

    private final ConversationRetentionRepository repository;
    private final ConversationRetentionProperties properties;
    private final Clock clock;

    @Transactional
    public CleanupResult run() {
        return run(clock.instant());
    }

    @Transactional
    public CleanupResult run(Instant now) {
        if (!properties.enabled()) {
            StructuredEventLog.info(log, "RETENTION_CLEANUP_SKIPPED", java.util.Map.of(
                    "operation", "conversation.retention.cleanup",
                    "reason", "DISABLED"));
            return new CleanupResult(0, 0, false);
        }

        try {
            int batchSize = properties.effectiveCleanupBatchSize();
            int redacted = repository.redactMessageBodiesBefore(
                    now.minus(properties.effectiveContentRetention()),
                    REDACTED_BODY,
                    batchSize);
            int deleted = repository.deleteMessagesBefore(
                    now.minus(properties.effectiveMetadataRetention()),
                    batchSize);
            StructuredEventLog.info(log, "RETENTION_CLEANUP_COMPLETED", java.util.Map.of(
                    "operation", "conversation.retention.cleanup",
                    "contentRedacted", redacted,
                    "metadataDeleted", deleted,
                    "aggregateMetricsUntouched", true,
                    "aggregateMetricsRetentionDays", properties.effectiveAggregateMetricsRetention().toDays()));
            return new CleanupResult(redacted, deleted, true);
        } catch (RuntimeException exception) {
            StructuredEventLog.warn(log, "RETENTION_CLEANUP_FAILED", java.util.Map.of(
                    "operation", "conversation.retention.cleanup",
                    "errorType", exception.getClass().getSimpleName()));
            throw exception;
        }
    }

    public record CleanupResult(int contentRedacted, int metadataDeleted, boolean executed) {
    }
}
