package com.wally.customersupport.shared.infrastructure.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.agent-runtime")
public record AgentRuntimeProperties(
        boolean activationEnabled,
        String environment,
        boolean shadowEnabled,
        Duration shadowTimeout,
        String shadowProvider,
        String shadowAllowedEnvironments,
        Integer shadowTrafficPercentage) {

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

    public String effectiveShadowProvider() {
        return shadowProvider == null || shadowProvider.isBlank()
                ? "noop"
                : shadowProvider.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public Set<String> effectiveShadowAllowedEnvironments() {
        if (shadowAllowedEnvironments == null || shadowAllowedEnvironments.isBlank()) {
            return Set.of("test");
        }
        return Arrays.stream(shadowAllowedEnvironments.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public boolean shadowEnvironmentAllowed() {
        return effectiveShadowAllowedEnvironments().contains(effectiveEnvironment().toLowerCase(Locale.ROOT));
    }

    public int effectiveShadowTrafficPercentage() {
        return shadowTrafficPercentage == null || shadowTrafficPercentage < 0 || shadowTrafficPercentage > 100
                ? 0
                : shadowTrafficPercentage;
    }
}
