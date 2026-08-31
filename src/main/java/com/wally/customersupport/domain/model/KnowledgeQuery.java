package com.wally.customersupport.domain.model;

import java.util.UUID;

public record KnowledgeQuery(String text, UUID conversationId, int maxResults) {
}
