package com.wally.customersupport.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.agent-runtime")
public record AgentRuntimeProperties(
        boolean activationEnabled,
        String environment) {

    public String effectiveEnvironment() {
        return environment == null || environment.isBlank()
                ? "prod"
                : environment.trim();
    }
}
