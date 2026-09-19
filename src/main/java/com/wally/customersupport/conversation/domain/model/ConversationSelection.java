package com.wally.customersupport.conversation.domain.model;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;

/**
 * Typed, bounded state for the active conversational selection.
 *
 * <p>This is context only. Catalog facts, stock, prices, orders and payments
 * remain authoritative in their own services.</p>
 */
public record ConversationSelection(
        ConversationIntent intent,
        ConversationAction action,
        CatalogQuery catalogQuery,
        String selectedVariantSku,
        String stage) {

    public ConversationSelection {
        intent = intent == null ? ConversationIntent.UNKNOWN : intent;
        action = action == null ? ConversationAction.fromIntent(intent) : action;
        catalogQuery = catalogQuery == null ? CatalogQuery.empty() : catalogQuery;
        selectedVariantSku = normalize(selectedVariantSku);
        stage = normalize(stage);
    }

    public static ConversationSelection empty() {
        return new ConversationSelection(
                ConversationIntent.UNKNOWN,
                ConversationAction.UNKNOWN,
                CatalogQuery.empty(),
                null,
                null);
    }

    public boolean hasCatalogSelection() {
        return !catalogQuery.isEmpty();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
