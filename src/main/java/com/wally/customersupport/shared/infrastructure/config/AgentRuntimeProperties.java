package com.wally.customersupport.shared.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.agent-runtime")
public record AgentRuntimeProperties(
        boolean activationEnabled,
        String environment,
        boolean shadowEnabled,
        Duration shadowTimeout) {

    public String effectiveEnvironment() {
        return environment == null || environment.isBlank()
                ? "prod"
                : environment.trim();
    }

    public Duration effectiveShadowTimeout() {
        return shadowTimeout == null || shadowTimeout.isZero() || shadowTimeout.isNegative()
                ? Duration.ofSeconds(5)
                : shadowTimeout;
    }
}
