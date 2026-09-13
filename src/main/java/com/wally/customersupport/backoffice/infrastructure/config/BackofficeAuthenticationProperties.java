package com.wally.customersupport.backoffice.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.backoffice.auth")
public record BackofficeAuthenticationProperties(
        boolean enabled,
        boolean secureCookies,
        String sameSite,
        String accessCookieName,
        String refreshCookieName,
        long refreshCookieMaxAgeSeconds,
        String corsAllowedOrigins,
        Cognito cognito) {

    public BackofficeAuthenticationProperties {
        sameSite = defaultText(sameSite, "Lax");
        accessCookieName = defaultText(accessCookieName, "wcs_backoffice_access");
        refreshCookieName = defaultText(refreshCookieName, "wcs_backoffice_refresh");
        corsAllowedOrigins = defaultText(corsAllowedOrigins, "http://localhost:5173");
        cognito = cognito == null ? new Cognito("us-east-1", "") : cognito;
    }

    public String normalizedSameSite() {
        return switch (sameSite.strip().toLowerCase()) {
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> "Lax";
        };
    }

    public long normalizedRefreshCookieMaxAgeSeconds() {
        return refreshCookieMaxAgeSeconds > 0 ? refreshCookieMaxAgeSeconds : 2_592_000;
    }

    public record Cognito(String region, String clientId) {
        public Cognito {
            region = defaultText(region, "us-east-1");
            clientId = clientId == null ? "" : clientId;
        }
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
