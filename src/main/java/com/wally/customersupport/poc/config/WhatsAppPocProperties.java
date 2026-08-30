package com.wally.customersupport.poc.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.poc.whatsapp")
public record WhatsAppPocProperties(
        String graphApiVersion,
        String graphApiBaseUrl,
        String phoneNumberId,
        String businessAccountId,
        String accessToken,
        String verifyToken,
        String appSecret,
        String allowedRecipient,
        String fixedReply,
        String replyMode,
        String templateName,
        String templateLanguageCode,
        String templateBodyParameters,
        Duration connectTimeout,
        Duration readTimeout) {
}
