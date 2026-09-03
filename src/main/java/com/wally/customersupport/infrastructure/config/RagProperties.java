package com.wally.customersupport.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.rag")
public record RagProperties(
        String provider,
        int maxResults,
        String knowledgeBaseId,
        String region) {

    public String effectiveRegion() {
        return region == null || region.isBlank() ? "us-east-1" : region;
    }

    public String requiredKnowledgeBaseId() {
        if (knowledgeBaseId == null || knowledgeBaseId.isBlank()) {
            throw new IllegalStateException("wcs.rag.knowledge-base-id must be configured for bedrock-kb");
        }
        return knowledgeBaseId;
    }
}
