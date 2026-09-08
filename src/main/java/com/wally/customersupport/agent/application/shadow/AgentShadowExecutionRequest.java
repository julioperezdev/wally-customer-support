package com.wally.customersupport.agent.application.shadow;

import java.util.Objects;

import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.Channel;

/**
 * Bounded input for a candidate execution. The request stays inside the
 * application boundary and is never serialized into logs or comparison
 * events.
 */
public record AgentShadowExecutionRequest(
        AgentRuntimeDefinition definition,
        Channel channel,
        String useCase,
        CatalogQuery catalogQuery,
        String sanitizedReferenceResponse) {

    public AgentShadowExecutionRequest {
        definition = Objects.requireNonNull(definition, "definition");
        channel = Objects.requireNonNull(channel, "channel");
        useCase = required(useCase, "useCase");
        sanitizedReferenceResponse = normalizeReference(sanitizedReferenceResponse);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeReference(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String bounded = value.strip()
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\p{Cntrl}", " ");
        return bounded.length() <= 4_000 ? bounded : bounded.substring(0, 4_000);
    }
}
