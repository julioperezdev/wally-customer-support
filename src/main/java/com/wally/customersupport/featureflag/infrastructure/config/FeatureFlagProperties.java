package com.wally.customersupport.featureflag.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.feature-flags")
public record FeatureFlagProperties(
        boolean enabled,
        boolean failClosed,
        long pollIntervalMs,
        long staleAfterMs,
        AppConfig appconfig,
        Publisher publisher) {

    public long effectivePollIntervalMs() {
        return pollIntervalMs < 1 ? 30_000L : pollIntervalMs;
    }

    public long effectiveStaleAfterMs() {
        return staleAfterMs < effectivePollIntervalMs() ? 300_000L : staleAfterMs;
    }

    public record AppConfig(
            String application,
            String environment,
            String profile) {
    }

    public record Publisher(
            boolean enabled,
            String deploymentStrategyId) {
    }
}
