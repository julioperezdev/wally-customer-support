package com.wally.customersupport.shared.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.inbound")
public record InboundProcessingProperties(
        int pollIntervalMs,
        int batchSize,
        int maxAttempts,
        Duration leaseDuration,
        Duration retryDelay) {
}
