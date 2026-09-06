package com.wally.customersupport.shared.infrastructure.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.conversation.preferences")
public record ConversationPreferenceProperties(
        boolean enabled,
        Duration ttl,
        int maxPreferences,
        int maxValueCharacters) {

    public Duration effectiveTtl() {
        return ttl == null || ttl.isNegative() || ttl.isZero()
                ? Duration.ofHours(24)
                : ttl;
    }

    public int effectiveMaxPreferences() {
        return maxPreferences <= 0 ? 5 : maxPreferences;
    }

    public int effectiveMaxValueCharacters() {
        return maxValueCharacters <= 0 ? 64 : maxValueCharacters;
    }
}
