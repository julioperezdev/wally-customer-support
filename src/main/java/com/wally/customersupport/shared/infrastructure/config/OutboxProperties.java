package com.wally.customersupport.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.outbox")
public record OutboxProperties(
        int pollIntervalMs,
        int maxAttempts) {
}
