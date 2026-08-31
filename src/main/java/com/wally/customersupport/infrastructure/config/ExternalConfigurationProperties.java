package com.wally.customersupport.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.external-config")
public record ExternalConfigurationProperties(
        AppConfig appconfig,
        SecretsManager secretsManager) {

    public record AppConfig(String application, String environment, String profile) {
    }

    public record SecretsManager(String secretId) {
    }
}
