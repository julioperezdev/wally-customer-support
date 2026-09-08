package com.wally.customersupport.agent.application.shadow;

import java.util.Objects;

import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.conversation.domain.model.ConversationContext;

/**
 * Bounded input for a candidate execution. The request stays inside the
 * application boundary and is never serialized into logs or comparison
 * events.
 */
public record AgentShadowExecutionRequest(
        AgentRuntimeDefinition definition,
        ConversationContext context,
        String useCase) {

    public AgentShadowExecutionRequest {
        definition = Objects.requireNonNull(definition, "definition");
        context = Objects.requireNonNull(context, "context");
        useCase = required(useCase, "useCase");
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
