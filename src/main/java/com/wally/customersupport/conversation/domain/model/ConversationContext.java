package com.wally.customersupport.conversation.domain.model;

import java.util.List;
import java.util.UUID;

import com.wally.customersupport.knowledge.domain.model.KnowledgeChunk;

public record ConversationContext(
        UUID conversationId,
        String externalCustomerId,
        String latestMessage,
        List<String> recentMessages,
        List<KnowledgeChunk> knowledge) {

    public ConversationContext {
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
        knowledge = knowledge == null ? List.of() : List.copyOf(knowledge);
    }
}
