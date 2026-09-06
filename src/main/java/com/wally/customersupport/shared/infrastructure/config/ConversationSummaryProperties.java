package com.wally.customersupport.shared.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.conversation.summary")
public record ConversationSummaryProperties(
        boolean enabled,
        int triggerMessageCount,
        int recentMessageCount,
        int triggerCharacters,
        int maxSummaryCharacters) {

    public int effectiveTriggerMessageCount() {
        return Math.max(1, triggerMessageCount);
    }

    public int effectiveRecentMessageCount() {
        return Math.max(1, Math.min(recentMessageCount, effectiveTriggerMessageCount()));
    }

    public int effectiveTriggerCharacters() {
        return Math.max(1, triggerCharacters);
    }

    public int effectiveMaxSummaryCharacters() {
        return Math.max(256, maxSummaryCharacters);
    }
}
