package com.wally.customersupport.conversation.application.service;

import com.wally.customersupport.catalog.application.service.CatalogSearchResult;
import com.wally.customersupport.conversation.domain.model.ConversationWorkingMemory;

/**
 * Response produced by a conversation use case before channel delivery.
 *
 * <p>The text is already validated by the use case, while the optional media
 * reference remains available to channel adapters such as Telegram.</p>
 */
public record ConversationRenderedResponse(
        String text,
        String mediaReference,
        ConversationWorkingMemory workingMemory) {

    public ConversationRenderedResponse(String text, String mediaReference) {
        this(text, mediaReference, ConversationWorkingMemory.empty());
    }

    public static ConversationRenderedResponse text(String text) {
        return new ConversationRenderedResponse(text, null);
    }

    public static ConversationRenderedResponse text(
            String text,
            ConversationWorkingMemory workingMemory) {
        return new ConversationRenderedResponse(text, null, workingMemory);
    }

    public static ConversationRenderedResponse catalog(String text, CatalogSearchResult result) {
        return new ConversationRenderedResponse(
                text,
                result == null ? null : result.singleImageReference().orElse(null),
                ConversationWorkingMemory.empty());
    }

    public static ConversationRenderedResponse catalog(
            String text,
            CatalogSearchResult result,
            ConversationWorkingMemory workingMemory) {
        return new ConversationRenderedResponse(
                text,
                result == null ? null : result.singleImageReference().orElse(null),
                workingMemory);
    }
}
