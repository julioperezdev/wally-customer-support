package com.wally.customersupport.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.ai")
public record AiProperties(
        String provider,
        String model,
        String mockReply) {
}
