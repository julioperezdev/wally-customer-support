package com.wally.customersupport.agent.application.service;

import java.util.List;
import java.util.Objects;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;

/**
 * Typed input accepted by the first catalog specialist boundary.
 *
 * <p>The request contains bounded catalog filters and conversation context
 * needed to resolve a follow-up. It intentionally has no SQL, prompt text,
 * or arbitrary tool arguments.</p>
 */
public record CatalogSpecialistExecutionRequest(
        AgentRuntimeDefinition definition,
        CatalogQuery catalogQuery,
        List<String> recentMessages,
        String latestMessage) {

    public CatalogSpecialistExecutionRequest {
        definition = Objects.requireNonNull(definition, "definition");
        recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    }
}
