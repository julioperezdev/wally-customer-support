package com.wally.customersupport.conversation.domain.model;

import java.util.Locale;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;

public record ConversationIntentDecision(
        ConversationIntent intent,
        double confidence,
        CatalogQuery catalogQuery,
        String policyKey) {

    public ConversationIntentDecision {
        intent = intent == null ? ConversationIntent.UNKNOWN : intent;
        confidence = Double.isFinite(confidence)
                ? Math.max(0.0, Math.min(1.0, confidence))
                : 0.0;
        policyKey = policyKey == null || policyKey.isBlank()
                ? null
                : policyKey.trim().toLowerCase(Locale.ROOT);
    }

    public static ConversationIntentDecision unknown() {
        return new ConversationIntentDecision(ConversationIntent.UNKNOWN, 0.0, null, null);
    }
}
