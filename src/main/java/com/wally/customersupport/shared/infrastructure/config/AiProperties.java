package com.wally.customersupport.shared.infrastructure.config;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.ai")
public record AiProperties(
        String provider,
        String model,
        String region,
        String pricingVersion,
        BigDecimal inputPriceUsdPerMillionTokens,
        BigDecimal outputPriceUsdPerMillionTokens,
        Duration requestTimeout) {

    @ConstructorBinding
    public AiProperties {
    }

    public AiProperties(
            String provider,
            String model,
            String region,
            String pricingVersion,
            BigDecimal inputPriceUsdPerMillionTokens,
            BigDecimal outputPriceUsdPerMillionTokens) {
        this(
                provider,
                model,
                region,
                pricingVersion,
                inputPriceUsdPerMillionTokens,
                outputPriceUsdPerMillionTokens,
                Duration.ofSeconds(30));
    }

    public String effectiveModel() {
        return model == null || model.isBlank() ? "openai.gpt-oss-20b-1:0" : model;
    }

    public String effectiveRegion() {
        return region == null || region.isBlank() ? "us-east-1" : region;
    }

    public String effectivePricingVersion() {
        return pricingVersion == null || pricingVersion.isBlank()
                ? "aws-bedrock-us-east-1-standard-2026-09"
                : pricingVersion;
    }

    public BigDecimal effectiveInputPriceUsdPerMillionTokens() {
        return nonNegative(inputPriceUsdPerMillionTokens);
    }

    public BigDecimal effectiveOutputPriceUsdPerMillionTokens() {
        return nonNegative(outputPriceUsdPerMillionTokens);
    }

    public Duration effectiveRequestTimeout() {
        Duration fallback = Duration.ofSeconds(30);
        if (requestTimeout == null
                || requestTimeout.isZero()
                || requestTimeout.isNegative()
                || requestTimeout.compareTo(Duration.ofSeconds(60)) > 0) {
            return fallback;
        }
        return requestTimeout;
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
    }
}
