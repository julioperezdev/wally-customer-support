package com.wally.customersupport.agent.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/** Declarative prompt and schema content for one immutable agent version. */
public record AgentInvocationConfiguration(
        String systemPrompt,
        String userPromptTemplate,
        String inputSchemaJson,
        String outputSchemaJson,
        String reasoningEffort,
        boolean structuredToolCalling,
        String pricingVersion,
        BigDecimal inputPriceUsdPerMillionTokens,
        BigDecimal outputPriceUsdPerMillionTokens) {

    public static final int MAX_PROMPT_CHARACTERS = 40_000;
    public static final int MAX_SCHEMA_CHARACTERS = 32_000;

    public AgentInvocationConfiguration(
            String systemPrompt,
            String userPromptTemplate,
            String inputSchemaJson,
            String outputSchemaJson,
            String reasoningEffort,
            boolean structuredToolCalling) {
        this(systemPrompt, userPromptTemplate, inputSchemaJson, outputSchemaJson, reasoningEffort,
                structuredToolCalling, null, null, null);
    }

    public AgentInvocationConfiguration {
        systemPrompt = bounded(systemPrompt, MAX_PROMPT_CHARACTERS, "systemPrompt");
        userPromptTemplate = bounded(userPromptTemplate, MAX_PROMPT_CHARACTERS, "userPromptTemplate");
        inputSchemaJson = bounded(inputSchemaJson, MAX_SCHEMA_CHARACTERS, "inputSchemaJson");
        outputSchemaJson = bounded(outputSchemaJson, MAX_SCHEMA_CHARACTERS, "outputSchemaJson");
        reasoningEffort = normalize(reasoningEffort);
        pricingVersion = normalize(pricingVersion);
        if ((inputPriceUsdPerMillionTokens == null) != (outputPriceUsdPerMillionTokens == null)) {
            throw new IllegalArgumentException("input and output token prices must be configured together");
        }
        if ((pricingVersion == null) != (inputPriceUsdPerMillionTokens == null)) {
            throw new IllegalArgumentException("pricingVersion and token prices must be configured together");
        }
        if (inputPriceUsdPerMillionTokens != null
                && (inputPriceUsdPerMillionTokens.signum() < 0 || outputPriceUsdPerMillionTokens.signum() < 0)) {
            throw new IllegalArgumentException("token prices must not be negative");
        }
        if (reasoningEffort != null && !SetHolder.ALLOWED_REASONING_EFFORTS.contains(reasoningEffort)) {
            throw new IllegalArgumentException("reasoningEffort must be low, medium, or high");
        }
    }

    public static AgentInvocationConfiguration empty() {
        return new AgentInvocationConfiguration("", "", "{}", "{}", null, false, null, null, null);
    }

    public static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte current : digest) result.append(String.format("%02x", current));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Avoid leaking prompt bodies or schemas through accidental object logging. */
    @Override
    public String toString() {
        return "AgentInvocationConfiguration[systemPrompt=<redacted>, userPromptTemplate=<redacted>, "
                + "inputSchemaJson=<redacted>, outputSchemaJson=<redacted>, reasoningEffort="
                + reasoningEffort + ", structuredToolCalling=" + structuredToolCalling
                + ", pricingVersion=" + pricingVersion + ", inputPriceUsdPerMillionTokens="
                + inputPriceUsdPerMillionTokens + ", outputPriceUsdPerMillionTokens="
                + outputPriceUsdPerMillionTokens + "]";
    }

    private static String bounded(String value, int max, String field) {
        String normalized = Objects.requireNonNullElse(value, "").strip();
        if (normalized.length() > max) {
            throw new IllegalArgumentException(field + " exceeds the maximum length");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized;
    }

    private static final class SetHolder {
        private static final java.util.Set<String> ALLOWED_REASONING_EFFORTS = java.util.Set.of("low", "medium", "high");
    }
}
