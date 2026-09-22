package com.wally.customersupport.shared.infrastructure.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

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
        Duration requestTimeout,
        StructuredToolCalling structuredToolCalling,
        Router router) {

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
                Duration.ofSeconds(30),
                new StructuredToolCalling(false),
                new Router(null, null, null, null, null));
    }

    public AiProperties(
            String provider,
            String model,
            String region,
            String pricingVersion,
            BigDecimal inputPriceUsdPerMillionTokens,
            BigDecimal outputPriceUsdPerMillionTokens,
            Duration requestTimeout) {
        this(
                provider,
                model,
                region,
                pricingVersion,
                inputPriceUsdPerMillionTokens,
                outputPriceUsdPerMillionTokens,
                requestTimeout,
                new StructuredToolCalling(false),
                new Router(null, null, null, null, null));
    }

    public AiProperties(
            String provider,
            String model,
            String region,
            String pricingVersion,
            BigDecimal inputPriceUsdPerMillionTokens,
            BigDecimal outputPriceUsdPerMillionTokens,
            Duration requestTimeout,
            boolean structuredToolCallingEnabled) {
        this(
                provider,
                model,
                region,
                pricingVersion,
                inputPriceUsdPerMillionTokens,
                outputPriceUsdPerMillionTokens,
                requestTimeout,
                new StructuredToolCalling(structuredToolCallingEnabled),
                new Router(null, null, null, null, null));
    }

    public AiProperties(
            String provider,
            String model,
            String region,
            String pricingVersion,
            BigDecimal inputPriceUsdPerMillionTokens,
            BigDecimal outputPriceUsdPerMillionTokens,
            Duration requestTimeout,
            boolean structuredToolCallingEnabled,
            Router router) {
        this(
                provider,
                model,
                region,
                pricingVersion,
                inputPriceUsdPerMillionTokens,
                outputPriceUsdPerMillionTokens,
                requestTimeout,
                new StructuredToolCalling(structuredToolCallingEnabled),
                router);
    }

    public boolean structuredToolCallingEnabled() {
        return structuredToolCalling != null && structuredToolCalling.enabled();
    }

    public record StructuredToolCalling(boolean enabled) {
    }

    /**
     * Model selection for the semantic conversation router. It is deliberately
     * separate from the default model so changing routing does not silently
     * change response generation or any other Bedrock call.
     */
    public record Router(
            String model,
            String version,
            String pricingVersion,
            BigDecimal inputPriceUsdPerMillionTokens,
            BigDecimal outputPriceUsdPerMillionTokens,
            String reasoningEffort) {

        private static final Set<String> SUPPORTED_REASONING_EFFORTS = Set.of("low", "medium", "high");

        public Router(
                String model,
                String version,
                String pricingVersion,
                BigDecimal inputPriceUsdPerMillionTokens,
                BigDecimal outputPriceUsdPerMillionTokens) {
            this(model, version, pricingVersion, inputPriceUsdPerMillionTokens,
                    outputPriceUsdPerMillionTokens, null);
        }

        public Router {
            reasoningEffort = reasoningEffort == null || reasoningEffort.isBlank()
                    ? null
                    : reasoningEffort.trim().toLowerCase(Locale.ROOT);
            if (reasoningEffort != null && !SUPPORTED_REASONING_EFFORTS.contains(reasoningEffort)) {
                throw new IllegalArgumentException("Unsupported router reasoning effort");
            }
        }
    }

    public record ModelSettings(
            String modelId,
            String version,
            String pricingVersion,
            BigDecimal inputPriceUsdPerMillionTokens,
            BigDecimal outputPriceUsdPerMillionTokens,
            String agentId,
            String agentVersion) {
    }

    public String effectiveModel() {
        return model == null || model.isBlank() ? "openai.gpt-oss-20b-1:0" : model;
    }

    public ModelSettings effectiveDefaultModelSettings() {
        return new ModelSettings(
                effectiveModel(),
                null,
                effectivePricingVersion(),
                effectiveInputPriceUsdPerMillionTokens(),
                effectiveOutputPriceUsdPerMillionTokens(),
                null,
                null);
    }

    public ModelSettings effectiveRouterModelSettings() {
        Router configured = router == null ? new Router(null, null, null, null, null) : router;
        return new ModelSettings(
                nonBlank(configured.model(), effectiveModel()),
                nonBlank(configured.version(), "conversation-router-v1"),
                nonBlank(configured.pricingVersion(), effectivePricingVersion()),
                nonNegative(configured.inputPriceUsdPerMillionTokens(), effectiveInputPriceUsdPerMillionTokens()),
                nonNegative(configured.outputPriceUsdPerMillionTokens(), effectiveOutputPriceUsdPerMillionTokens()),
                "conversation-router",
                nonBlank(configured.version(), "conversation-router-v1"));
    }

    public String effectiveRouterReasoningEffort() {
        return router == null || router.reasoningEffort() == null ? "high" : router.reasoningEffort();
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

    private static BigDecimal nonNegative(BigDecimal value, BigDecimal fallback) {
        return value == null || value.signum() < 0 ? fallback : value;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
