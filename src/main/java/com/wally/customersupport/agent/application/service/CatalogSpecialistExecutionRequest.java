package com.wally.customersupport.agent.application.service;

import java.util.Objects;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;

/**
 * Typed input accepted by the first catalog specialist boundary.
 *
 * <p>The router has already reconciled the conversation into a typed catalog
 * query. This boundary receives only that query and the current turn needed
 * for narrow catalog status checks, never an unbounded transcript.</p>
 */
public record CatalogSpecialistExecutionRequest(
        AgentRuntimeDefinition definition,
        CatalogQuery catalogQuery,
        String latestMessage) {

    public CatalogSpecialistExecutionRequest {
        definition = Objects.requireNonNull(definition, "definition");
    }
}
