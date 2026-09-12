package com.wally.customersupport.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Sensitive observability settings resolved from Secrets Manager at startup. */
@ConfigurationProperties(prefix = "wcs.observability")
public record ObservabilityProperties(String actorKeySecret) {

    public String effectiveActorKeySecret() {
        return actorKeySecret == null ? "" : actorKeySecret.strip();
    }
}
