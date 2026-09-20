package com.wally.customersupport.conversation.application.service;

import com.wally.customersupport.catalog.application.service.CatalogSearchResult;

/**
 * Response produced by a conversation use case before channel delivery.
 *
 * <p>The text is already validated by the use case, while the optional media
 * reference remains available to channel adapters such as Telegram.</p>
 */
public record ConversationRenderedResponse(String text, String mediaReference) {

    public static ConversationRenderedResponse text(String text) {
        return new ConversationRenderedResponse(text, null);
    }

    public static ConversationRenderedResponse catalog(String text, CatalogSearchResult result) {
        return new ConversationRenderedResponse(
                text,
                result == null ? null : result.singleImageReference().orElse(null));
    }
}
