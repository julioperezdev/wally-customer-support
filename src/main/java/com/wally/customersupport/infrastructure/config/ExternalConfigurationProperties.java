package com.wally.customersupport.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.external-config")
public record ExternalConfigurationProperties(
        AppConfig appconfig,
        SecretsManager secretsManager) {

    public record AppConfig(
            String application,
            String environment,
            String profile,
            boolean enabled,
            boolean failFast) {
    }

    public record SecretsManager(
            String secretId,
            String runtimeSecretId,
            String databaseSecretId,
            String whatsappSecretId,
            String telegramSecretId,
            boolean enabled,
            boolean failFast) {
    }
}
