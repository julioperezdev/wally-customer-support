package com.wally.customersupport.domain.model;

import java.util.List;
import java.util.UUID;

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
