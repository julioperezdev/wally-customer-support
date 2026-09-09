package com.wally.customersupport.shared.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.conversation.retention")
public record ConversationRetentionProperties(
        boolean enabled,
        Duration contentRetention,
        Duration metadataRetention,
        Duration aggregateMetricsRetention,
        int cleanupBatchSize,
        long scheduleDelayMs) {

    public Duration effectiveContentRetention() {
        return positiveOrDefault(contentRetention, Duration.ofDays(30));
    }

    public Duration effectiveMetadataRetention() {
        return positiveOrDefault(metadataRetention, Duration.ofDays(90));
    }

    public Duration effectiveAggregateMetricsRetention() {
        return positiveOrDefault(aggregateMetricsRetention, Duration.ofDays(365));
    }

    public int effectiveCleanupBatchSize() {
        return cleanupBatchSize <= 0 ? 500 : cleanupBatchSize;
    }

    public long effectiveScheduleDelayMs() {
        return scheduleDelayMs <= 0 ? Duration.ofDays(1).toMillis() : scheduleDelayMs;
    }

    private static Duration positiveOrDefault(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
