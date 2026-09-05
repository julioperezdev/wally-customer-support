package com.wally.customersupport.shared.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.whatsapp")
public record WhatsAppProperties(
        String adapter,
        String graphApiVersion,
        String graphApiBaseUrl,
        String phoneNumberId,
        String businessAccountId,
        String accessToken,
        String verifyToken,
        String appSecret,
        String allowedRecipient,
        Duration connectTimeout,
        Duration readTimeout) {
}
