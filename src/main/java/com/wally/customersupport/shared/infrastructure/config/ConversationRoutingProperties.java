package com.wally.customersupport.shared.infrastructure.config;

import java.time.Duration;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.ai.routing")
public record ConversationRoutingProperties(
        String provider,
        String mode,
        double minimumConfidence,
        TypeSafe typesafe) {

    private static final double DEFAULT_MINIMUM_CONFIDENCE = 0.65;

    public ConversationRoutingProperties {
        provider = normalize(provider, "bedrock");
        mode = normalize(mode, "off");
        if (!Double.isFinite(minimumConfidence) || minimumConfidence <= 0.0 || minimumConfidence > 1.0) {
            minimumConfidence = DEFAULT_MINIMUM_CONFIDENCE;
        }
        typesafe = typesafe == null ? TypeSafe.defaults() : typesafe;
    }

    public static ConversationRoutingProperties defaults() {
        return new ConversationRoutingProperties("bedrock", "off", DEFAULT_MINIMUM_CONFIDENCE, TypeSafe.defaults());
    }

    public boolean typeSafeEnabled() {
        return "typesafe".equals(provider) && ("shadow".equals(mode) || "active".equals(mode));
    }

    public boolean shadowMode() {
        return typeSafeEnabled() && "shadow".equals(mode);
    }

    public boolean activeMode() {
        return typeSafeEnabled() && "active".equals(mode);
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value.trim().toLowerCase(Locale.ROOT);
    }

    public record TypeSafe(
            String endpoint,
            String model,
            String apiKey,
            Duration requestTimeout,
            int maxHistoryMessages,
            int maxStateCharacters) {

        public TypeSafe {
            endpoint = endpoint == null || endpoint.isBlank()
                    ? "https://api.typesafe.ai"
                    : endpoint.trim().replaceAll("/+$", "");
            model = model == null || model.isBlank() ? "jev-1.13.0" : model.trim();
            apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.trim();
            requestTimeout = requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()
                    ? Duration.ofSeconds(5)
                    : requestTimeout.compareTo(Duration.ofSeconds(30)) > 0
                            ? Duration.ofSeconds(30)
                            : requestTimeout;
            maxHistoryMessages = Math.max(0, Math.min(maxHistoryMessages, 12));
            maxStateCharacters = maxStateCharacters < 128
                    ? 2_000
                    : Math.min(maxStateCharacters, 8_000);
        }

        public static TypeSafe defaults() {
            return new TypeSafe(
                    "https://api.typesafe.ai",
                    "jev-1.13.0",
                    null,
                    Duration.ofSeconds(5),
                    6,
                    2_000);
        }

        @Override
        public String toString() {
            return "TypeSafe[endpoint=" + endpoint
                    + ", model=" + model
                    + ", apiKeyConfigured=" + (apiKey != null)
                    + ", requestTimeout=" + requestTimeout
                    + ", maxHistoryMessages=" + maxHistoryMessages
                    + ", maxStateCharacters=" + maxStateCharacters + "]";
        }
    }
}
