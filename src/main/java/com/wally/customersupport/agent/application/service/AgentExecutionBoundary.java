package com.wally.customersupport.agent.application.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionPlan;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.shared.infrastructure.config.AgentRuntimeProperties;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Owns the runtime boundary around activation, definition validation and
 * shadow execution.
 *
 * <p>The conversation orchestrator receives the resulting immutable snapshot
 * and does not need to know how feature flags, registry versions or shadow
 * safety rules are resolved.</p>
 */
@Service
@Slf4j
public class AgentExecutionBoundary {

    private final AgentActivationResolver activationResolver;
    private final AgentRuntimeDefinitionResolver definitionResolver;
    private final AgentRuntimeProperties runtimeProperties;
    private final AgentShadowRuntimeService shadowRuntimeService;

    @Autowired
    public AgentExecutionBoundary(
            AgentActivationResolver activationResolver,
            AgentRuntimeDefinitionResolver definitionResolver,
            AgentRuntimeProperties runtimeProperties,
            AgentShadowRuntimeService shadowRuntimeService) {
        this.activationResolver = activationResolver;
        this.definitionResolver = definitionResolver;
        this.runtimeProperties = runtimeProperties;
        this.shadowRuntimeService = shadowRuntimeService;
    }

    public Resolution resolve(ConversationContext context, ConversationExecutionPlan plan) {
        AgentActivationKey key = resolveActivationKey(context, plan);
        AgentActivationResolution activation = resolveActivation(key, plan);
        AgentRuntimeDefinitionResolution definition = key == null
                ? null
                : definitionResolver.resolve(key);
        AgentRuntimeDefinitionResolution validatedDefinition = definitionResolver.validateForExecution(
                definition,
                plan);
        if (validatedDefinition != null) {
            definition = validatedDefinition;
        }
        return new Resolution(key, activation, definition);
    }

    public void executeShadowSafely(
            Resolution resolution,
            ConversationContext context,
            String useCase,
            ConversationIntentDecision decision,
            String activeResponse) {
        try {
            shadowRuntimeService.executeIfEnabled(
                    resolution == null ? null : resolution.definition(),
                    context,
                    useCase,
                    decision == null ? null : decision.catalogQuery(),
                    activeResponse);
        } catch (RuntimeException exception) {
            // Candidate evidence must never break the active customer response.
            StructuredEventLog.warn(log, "AGENT_SHADOW_EXECUTION_FAILED", Map.of(
                    "useCase", useCase,
                    "errorType", exception.getClass().getSimpleName()));
        }
    }

    private AgentActivationKey resolveActivationKey(
            ConversationContext context,
            ConversationExecutionPlan plan) {
        if (!runtimeProperties.activationEnabled()) {
            StructuredEventLog.info(log, "AGENT_ACTIVATION_RESOLUTION_SKIPPED", Map.of(
                    "useCase", plan.useCase(),
                    "reason", "CONFIG_DISABLED"));
            return null;
        }
        if (context == null || context.channel() == null) {
            StructuredEventLog.warn(log, "AGENT_ACTIVATION_FALLBACK", Map.of(
                    "useCase", plan.useCase(),
                    "reason", "CHANNEL_UNAVAILABLE"));
            return null;
        }

        String channel = context.channel().name().toLowerCase(Locale.ROOT);
        return new AgentActivationKey(
                plan.steps().getFirst().owner(),
                runtimeProperties.effectiveEnvironment(),
                channel,
                plan.useCase());
    }

    private AgentActivationResolution resolveActivation(
            AgentActivationKey key,
            ConversationExecutionPlan plan) {
        if (key == null) {
            return null;
        }
        AgentActivationResolution resolution = activationResolver.resolve(key);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("useCase", plan.useCase());
        fields.put("channel", key.channel());
        addActivationFields(fields, resolution);
        StructuredEventLog.info(log, "AGENT_ACTIVATION_RESOLVED", fields);
        return resolution;
    }

    private static void addActivationFields(
            Map<String, Object> fields,
            AgentActivationResolution activation) {
        if (activation == null) {
            return;
        }
        fields.put("activationStatus", activation.status().name());
        fields.put("activationReason", activation.reason().name());
        if (activation.isActive()) {
            fields.put("agentId", activation.agentId());
            fields.put("agentVersion", activation.agentVersion());
        }
    }

    public record Resolution(
            AgentActivationKey activationKey,
            AgentActivationResolution activation,
            AgentRuntimeDefinitionResolution definition) {
    }
}
