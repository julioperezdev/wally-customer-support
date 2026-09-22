package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;

import com.wally.customersupport.agent.application.service.AgentActivationKey;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinitionResolution;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinitionResolver;
import com.wally.customersupport.agent.domain.model.AgentInvocationConfiguration;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptTemplateRenderer;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import com.wally.customersupport.shared.infrastructure.config.AgentRuntimeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Resolves the active SQL-backed Bedrock profile on each call; it deliberately does not cache. */
@Component
@Slf4j
@ConditionalOnProperty(name = "wcs.ai.provider", havingValue = "bedrock")
public class BedrockAgentProfileResolver {

    private final AgentRuntimeDefinitionResolver definitionResolver;
    private final AgentRuntimeProperties runtimeProperties;
    private final ObjectMapper objectMapper;

    @Autowired
    public BedrockAgentProfileResolver(
            AgentRuntimeDefinitionResolver definitionResolver,
            AgentRuntimeProperties runtimeProperties,
            ObjectMapper objectMapper) {
        this.definitionResolver = definitionResolver;
        this.runtimeProperties = runtimeProperties;
        this.objectMapper = objectMapper;
    }

    public Optional<AgentRuntimeDefinition> resolve(
            String agentId,
            String useCase,
            ConversationContext context) {
        return resolve(agentId, useCase, context == null ? null : context.channel());
    }

    public Optional<AgentRuntimeDefinition> resolve(String agentId, String useCase, Channel channel) {
        if (!runtimeProperties.activationEnabled()) {
            StructuredEventLog.info(log, "BEDROCK_AGENT_PROFILE_RESOLUTION_SKIPPED", Map.of(
                    "agentId", agentId,
                    "useCase", useCase,
                    "reason", "CONFIG_DISABLED"));
            return Optional.empty();
        }
        if (channel == null) {
            return Optional.empty();
        }
        AgentRuntimeDefinitionResolution resolution = definitionResolver.resolve(new AgentActivationKey(
                agentId,
                runtimeProperties.effectiveEnvironment(),
                channel.name().toLowerCase(java.util.Locale.ROOT),
                useCase));
        if (resolution == null || !resolution.isActive()) {
            return Optional.empty();
        }
        AgentRuntimeDefinition definition = resolution.definition();
        String invalidReason = validate(definition);
        if (invalidReason != null) {
            StructuredEventLog.warn(log, "BEDROCK_AGENT_PROFILE_REJECTED", Map.of(
                    "agentId", definition.agentId(),
                    "semanticVersion", definition.semanticVersion(),
                    "useCase", useCase,
                    "reason", invalidReason));
            return Optional.empty();
        }
        return Optional.of(definition);
    }

    private String validate(AgentRuntimeDefinition definition) {
        if (!"bedrock".equalsIgnoreCase(definition.modelProvider())) return "UNSUPPORTED_PROVIDER";
        if (definition.invocationConfiguration().systemPrompt().isBlank()) return "SYSTEM_PROMPT_MISSING";
        if (definition.invocationConfiguration().userPromptTemplate().isBlank()) return "USER_TEMPLATE_MISSING";
        if (!AgentInvocationConfiguration.sha256(definition.invocationConfiguration().systemPrompt().trim())
                .equalsIgnoreCase(definition.systemPromptHash())) return "SYSTEM_PROMPT_HASH_MISMATCH";
        if (definition.invocationConfiguration().pricingVersion() == null
                || definition.invocationConfiguration().inputPriceUsdPerMillionTokens() == null
                || definition.invocationConfiguration().outputPriceUsdPerMillionTokens() == null) {
            return "PRICING_PROFILE_MISSING";
        }
        try {
            if (!objectMapper.readTree(definition.invocationConfiguration().inputSchemaJson()).isObject()
                    || !objectMapper.readTree(definition.invocationConfiguration().outputSchemaJson()).isObject()) {
                return "SCHEMA_NOT_OBJECT";
            }
            // Validate placeholder syntax and names before allowing the snapshot to reach Bedrock.
            PromptTemplateRenderer.render(definition.invocationConfiguration().userPromptTemplate(),
                    placeholderValues(definition.agentId()));
        } catch (RuntimeException exception) {
            return "INVALID_TEMPLATE_OR_SCHEMA";
        }
        return null;
    }

    private static Map<String, String> placeholderValues(String agentId) {
        return switch (agentId) {
            case "conversation-router" -> Map.of(
                    "prompt_version", "v",
                    "conversation_history", "",
                    "latest_customer_message", "",
                    "conversation_summary_section", "",
                    "customer_preferences_section", "",
                    "active_selection_section", "");
            case "response-generation" -> Map.of(
                    "latest_message", "",
                    "recent_messages", "",
                    "conversation_summary", "",
                    "active_selection", "",
                    "customer_preferences", "",
                    "approved_knowledge", "");
            case "response-humanization" -> Map.of(
                    "use_case", "",
                    "channel", "",
                    "approved_knowledge", "",
                    "required_facts", "");
            case "conversation-summarizer" -> Map.of(
                    "previous_summary", "",
                    "older_messages", "");
            default -> placeholderValuesForGenericProfile();
        };
    }

    private static Map<String, String> placeholderValuesForGenericProfile() {
        // Other existing registry agents do not use a Bedrock invocation profile today.
        return new LinkedHashMap<>();
    }
}
