package com.wally.customersupport.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.ai")
public record AiProperties(
        String provider,
        String model,
        String region) {

    public String effectiveModel() {
        return model == null || model.isBlank() ? "openai.gpt-oss-20b-1:0" : model;
    }

    public String effectiveRegion() {
        return region == null || region.isBlank() ? "us-east-1" : region;
    }
}
