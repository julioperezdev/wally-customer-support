package com.wally.customersupport.conversation.domain.model;

import java.util.Locale;
import java.util.List;
import java.util.Objects;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;

public record ConversationIntentDecision(
        ConversationIntent intent,
        ConversationAction action,
        double confidence,
        CatalogQuery catalogQuery,
        String policyKey,
        int quantity,
        List<String> missingParameters) {

    public ConversationIntentDecision(
            ConversationIntent intent,
            double confidence,
            CatalogQuery catalogQuery,
            String policyKey) {
        this(intent, ConversationAction.fromIntent(intent), confidence, catalogQuery, policyKey, 1, List.of());
    }

    public ConversationIntentDecision {
        intent = intent == null ? ConversationIntent.UNKNOWN : intent;
        action = action == null ? ConversationAction.fromIntent(intent) : action;
        confidence = Double.isFinite(confidence)
                ? Math.max(0.0, Math.min(1.0, confidence))
                : 0.0;
        policyKey = policyKey == null || policyKey.isBlank()
                ? null
                : policyKey.trim().toLowerCase(Locale.ROOT);
        quantity = quantity < 1 ? 1 : Math.min(quantity, 100);
        missingParameters = missingParameters == null
                ? List.of()
                : missingParameters.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .distinct()
                        .limit(8)
                        .toList();
    }

    public static ConversationIntentDecision unknown() {
        return new ConversationIntentDecision(
                ConversationIntent.UNKNOWN,
                ConversationAction.UNKNOWN,
                0.0,
                null,
                null,
                1,
                List.of());
    }
}
