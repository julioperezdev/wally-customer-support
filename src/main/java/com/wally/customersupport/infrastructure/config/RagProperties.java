package com.wally.customersupport.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.rag")
public record RagProperties(
        String provider,
        int maxResults) {
}
