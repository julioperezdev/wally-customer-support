package com.wally.customersupport.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.telegram")
public record TelegramProperties(
        boolean enabled,
        String adapter,
        String apiBaseUrl,
        String botToken,
        String webhookSecretToken,
        String allowedChatId,
        Duration connectTimeout,
        Duration readTimeout) {
}
