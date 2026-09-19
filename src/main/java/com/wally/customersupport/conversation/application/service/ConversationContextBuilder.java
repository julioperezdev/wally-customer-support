package com.wally.customersupport.conversation.application.service;

import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationState;
import com.wally.customersupport.conversation.domain.model.CustomerPreference;
import com.wally.customersupport.knowledge.domain.model.KnowledgeChunk;

/**
 * Builds the bounded input shared by the semantic router and the use cases.
 *
 * <p>The builder does not retrieve facts and does not interpret customer text.
 * It only preserves the bounded memory window, summary, preferences and typed
 * selection already owned by the conversation application.</p>
 */
public final class ConversationContextBuilder {

    public ConversationContext build(
            UUID conversationId,
            String externalCustomerId,
            String latestMessage,
            ConversationState state,
            List<KnowledgeChunk> knowledge,
            String conversationSummary,
            List<CustomerPreference> preferences,
            Channel channel) {
        return new ConversationContext(
                conversationId,
                externalCustomerId,
                latestMessage,
                state == null ? List.of() : state.recentMessages(),
                knowledge,
                conversationSummary,
                preferences,
                channel,
                state == null ? null : state.selection());
    }
}
