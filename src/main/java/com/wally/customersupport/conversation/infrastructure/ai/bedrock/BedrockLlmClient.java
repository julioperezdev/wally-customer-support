package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import java.util.stream.Collectors;
import java.util.Map;

import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.conversation.application.port.out.LlmClient;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.ClasspathPromptRegistry;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptDefinition;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptRegistry;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptTemplateRenderer;
import com.wally.customersupport.shared.infrastructure.config.AiResponseProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wcs.ai.provider", havingValue = "bedrock")
public class BedrockLlmClient implements LlmClient {

    private final BedrockConverseClient converseClient;
    private final AiResponseProperties responseProperties;
    private final PromptRegistry promptRegistry;
    private final PromptDefinition prompt;
    private final BedrockAgentProfileResolver profileResolver;

    public BedrockLlmClient(BedrockConverseClient converseClient) {
        this(
                converseClient,
                new AiResponseProperties(
                        "conversation-response-v1", 1_024, null, 2_000, 6, 8_000, 4_000),
                new ClasspathPromptRegistry(), null);
    }

    public BedrockLlmClient(
            BedrockConverseClient converseClient,
            AiResponseProperties responseProperties,
            PromptRegistry promptRegistry) {
        this(converseClient, responseProperties, promptRegistry, null);
    }

    @Autowired
    public BedrockLlmClient(
            BedrockConverseClient converseClient,
            AiResponseProperties responseProperties,
            PromptRegistry promptRegistry,
            BedrockAgentProfileResolver profileResolver) {
        this.converseClient = converseClient;
        this.responseProperties = responseProperties;
        this.promptRegistry = promptRegistry;
        this.prompt = promptRegistry.responsePrompt(responseProperties.effectivePromptVersion());
        this.profileResolver = profileResolver;
    }

    @Override
    public String generateReply(ConversationContext context) {
        AgentRuntimeDefinition profile = resolveProfile(context);
        if (profile != null) return generateReplyFromProfile(context, profile);
        return generateReply(
                context,
                prompt,
                responseProperties.effectiveMaxOutputTokens(),
                responseProperties.effectiveTemperature(),
                null);
    }

    @Override
    public String generateReply(
            ConversationContext context,
            AgentRuntimeDefinition definition) {
        AgentRuntimeDefinition profile = resolveProfile(context);
        if (profile != null) return generateReplyFromProfile(context, profile);
        if (definition == null) {
            return generateReply(context);
        }
        if (!"bedrock".equalsIgnoreCase(definition.modelProvider())) {
            throw new IllegalStateException("agent model provider is not supported by Bedrock");
        }

        PromptDefinition agentPrompt = promptRegistry.responsePrompt(definition.systemPromptVersion());
        if (!agentPrompt.sha256().equalsIgnoreCase(definition.systemPromptHash())) {
            throw new IllegalStateException("agent prompt hash does not match the published definition");
        }
        return generateReply(
                context,
                agentPrompt,
                definition.maxOutputTokens(),
                definition.inferenceParameters().temperature().floatValue(),
                definition);
    }

    private AgentRuntimeDefinition resolveProfile(ConversationContext context) {
        return profileResolver == null
                ? null
                : profileResolver.resolve("response-generation", "GENERAL_SUPPORT", context).orElse(null);
    }

    private String generateReplyFromProfile(ConversationContext context, AgentRuntimeDefinition definition) {
        String systemPrompt = definition.invocationConfiguration().systemPrompt();
        String userPrompt = PromptTemplateRenderer.render(
                definition.invocationConfiguration().userPromptTemplate(),
                Map.of(
                        "latest_message", limit(context.latestMessage(), effectiveInputCharacterLimit(definition)),
                        "recent_messages", context.recentMessages().stream()
                                .skip(Math.max(0, context.recentMessages().size()
                                        - responseProperties.effectiveMaxHistoryMessages()))
                                .map(value -> limit(value, effectiveInputCharacterLimit(definition)))
                                .collect(Collectors.joining("\n")),
                        "conversation_summary", limit(context.conversationSummary(),
                                responseProperties.effectiveMaxSummaryCharacters()),
                        // Typed selection/preferences belong to routing and catalog. Keep legacy
                        // placeholders renderable, but do not disclose that state to this agent.
                        "active_selection", "",
                        "customer_preferences", "",
                        "approved_knowledge", limit(context.knowledge().stream()
                                .map(chunk -> "[" + chunk.sourceId() + "] " + chunk.content())
                                .collect(Collectors.joining("\n")),
                                responseProperties.effectiveMaxKnowledgeCharacters())));
        String correlationId = context.conversationId() == null
                ? null
                : context.conversationId().toString();
        return correlationId == null
                ? converseClient.completeForAgent(
                        "response-generation", "conversation.reply.generate", systemPrompt, userPrompt,
                        definition.maxOutputTokens(), definition.inferenceParameters().temperature().floatValue(),
                        definition.inferenceParameters().topP().floatValue(), definition.semanticVersion(),
                        definition.systemPromptHash(), definition)
                : converseClient.completeForAgent(
                        "response-generation", "conversation.reply.generate", systemPrompt, userPrompt,
                        definition.maxOutputTokens(), definition.inferenceParameters().temperature().floatValue(),
                        definition.inferenceParameters().topP().floatValue(), definition.semanticVersion(),
                        definition.systemPromptHash(), definition, correlationId);
    }

    private String generateReply(
            ConversationContext context,
            PromptDefinition prompt,
            int maxOutputTokens,
            float temperature,
            AgentRuntimeDefinition definition) {
        int inputCharacterLimit = effectiveInputCharacterLimit(definition);
        String userPrompt = """
                <latest_message>
                %s
                </latest_message>
                <recent_messages>
                %s
                </recent_messages>
                <conversation_summary>
                %s
                </conversation_summary>
                <approved_knowledge>
                %s
                </approved_knowledge>
                """.formatted(
                limit(context.latestMessage(), inputCharacterLimit),
                context.recentMessages().stream()
                        .skip(Math.max(0, context.recentMessages().size()
                                - responseProperties.effectiveMaxHistoryMessages()))
                        .map(value -> limit(value, inputCharacterLimit))
                        .collect(Collectors.joining("\n")),
                limit(context.conversationSummary(), responseProperties.effectiveMaxSummaryCharacters()),
                limit(context.knowledge().stream()
                        .map(chunk -> "[" + chunk.sourceId() + "] " + chunk.content())
                        .collect(Collectors.joining("\n")), responseProperties.effectiveMaxKnowledgeCharacters()));
        if (definition == null) {
            String correlationId = context.conversationId() == null
                    ? null
                    : context.conversationId().toString();
            return correlationId == null
                    ? converseClient.complete(
                            "response-generation",
                            "conversation.reply.generate",
                            prompt.content(),
                            userPrompt,
                            maxOutputTokens,
                            temperature,
                            prompt.version(),
                            prompt.sha256())
                    : converseClient.complete(
                            "response-generation",
                            "conversation.reply.generate",
                            prompt.content(),
                            userPrompt,
                            maxOutputTokens,
                            temperature,
                            prompt.version(),
                            prompt.sha256(),
                            correlationId);
        }
        String correlationId = context.conversationId() == null
                ? null
                : context.conversationId().toString();
        return correlationId == null
                ? converseClient.completeForAgent(
                        "response-generation",
                        "conversation.reply.generate",
                        prompt.content(),
                        userPrompt,
                        maxOutputTokens,
                        temperature,
                        definition.inferenceParameters().topP().floatValue(),
                        prompt.version(),
                        prompt.sha256(),
                        definition)
                : converseClient.completeForAgent(
                        "response-generation",
                        "conversation.reply.generate",
                        prompt.content(),
                        userPrompt,
                        maxOutputTokens,
                        temperature,
                        definition.inferenceParameters().topP().floatValue(),
                        prompt.version(),
                        prompt.sha256(),
                        definition,
                        correlationId);
    }

    private int effectiveInputCharacterLimit(AgentRuntimeDefinition definition) {
        if (definition == null) {
            return responseProperties.effectiveMaxInputCharacters();
        }
        if ("response-generation".equals(definition.agentId())) {
            return Math.min(12_000, Math.multiplyExact(definition.maxInputTokens(), 4));
        }
        // A conservative four-character approximation keeps a versioned
        // token budget from being bypassed by an oversized user/context block.
        int definitionLimit = Math.multiplyExact(definition.maxInputTokens(), 4);
        return Math.min(responseProperties.effectiveMaxInputCharacters(), definitionLimit);
    }

    private static String limit(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }
}
