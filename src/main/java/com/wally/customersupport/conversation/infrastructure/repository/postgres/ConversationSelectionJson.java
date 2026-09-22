package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationSelection;

/**
 * Persistence-only JSON contract for the active conversational selection.
 *
 * <p>The domain model is intentionally not serialized directly. This keeps
 * computed domain methods out of the database format and lets the schema
 * evolve independently from the domain API.</p>
 */
public record ConversationSelectionJson(
        String intent,
        String action,
        CatalogQueryJson catalogQuery,
        String selectedVariantSku,
        String stage,
        ConversationWorkingMemoryJson workingMemory) {

    public ConversationSelectionJson(
            String intent,
            String action,
            CatalogQueryJson catalogQuery,
            String selectedVariantSku,
            String stage) {
        this(intent, action, catalogQuery, selectedVariantSku, stage, null);
    }

    static ConversationSelectionJson fromDomain(ConversationSelection selection) {
        ConversationSelection normalized = selection == null
                ? ConversationSelection.empty()
                : selection;
        return new ConversationSelectionJson(
                normalized.intent().name(),
                normalized.action().name(),
                CatalogQueryJson.fromDomain(normalized.catalogQuery()),
                normalized.selectedVariantSku(),
                normalized.stage(),
                ConversationWorkingMemoryJson.fromDomain(normalized.workingMemory()));
    }

    ConversationSelection toDomain() {
        CatalogQuery query = catalogQuery == null ? CatalogQuery.empty() : catalogQuery.toDomain();
        return new ConversationSelection(
                parseIntent(intent),
                parseAction(action),
                query,
                selectedVariantSku,
                stage,
                workingMemory == null
                        ? com.wally.customersupport.conversation.domain.model.ConversationWorkingMemory.empty()
                        : workingMemory.toDomain());
    }

    private static ConversationIntent parseIntent(String value) {
        if (value == null || value.isBlank()) {
            return ConversationIntent.UNKNOWN;
        }
        try {
            return ConversationIntent.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return ConversationIntent.UNKNOWN;
        }
    }

    private static ConversationAction parseAction(String value) {
        if (value == null || value.isBlank()) {
            return ConversationAction.UNKNOWN;
        }
        try {
            return ConversationAction.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return ConversationAction.UNKNOWN;
        }
    }
}
