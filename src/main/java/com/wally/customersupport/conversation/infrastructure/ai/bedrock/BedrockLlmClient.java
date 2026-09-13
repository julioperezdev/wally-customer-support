package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import java.util.stream.Collectors;

import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.conversation.application.port.out.LlmClient;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.ClasspathPromptRegistry;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptDefinition;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptRegistry;
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

    public BedrockLlmClient(BedrockConverseClient converseClient) {
        this(
                converseClient,
                new AiResponseProperties(
                        "conversation-response-v1", 1_024, null, 2_000, 6, 8_000, 4_000),
                new ClasspathPromptRegistry());
    }

    @Autowired
    public BedrockLlmClient(
            BedrockConverseClient converseClient,
            AiResponseProperties responseProperties,
            PromptRegistry promptRegistry) {
        this.converseClient = converseClient;
        this.responseProperties = responseProperties;
        this.promptRegistry = promptRegistry;
        this.prompt = promptRegistry.responsePrompt(responseProperties.effectivePromptVersion());
    }

    @Override
    public String generateReply(ConversationContext context) {
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
                <customer_preferences>
                %s
                </customer_preferences>
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
                limit(context.preferences().stream()
                        .map(preference -> preference.key() + "=" + preference.value())
                        .collect(Collectors.joining("\n")), 1_000),
                limit(context.knowledge().stream()
                        .map(chunk -> "[" + chunk.sourceId() + "] " + chunk.content())
                        .collect(Collectors.joining("\n")), responseProperties.effectiveMaxKnowledgeCharacters()));
        if (definition == null) {
            return converseClient.complete(
                    "response-generation",
                    "conversation.reply.generate",
                    prompt.content(),
                    userPrompt,
                    maxOutputTokens,
                    temperature,
                    prompt.version(),
                    prompt.sha256());
        }
        return converseClient.completeForAgent(
                "response-generation",
                "conversation.reply.generate",
                prompt.content(),
                userPrompt,
                maxOutputTokens,
                temperature,
                definition.inferenceParameters().topP().floatValue(),
                prompt.version(),
                prompt.sha256(),
                definition);
    }

    private int effectiveInputCharacterLimit(AgentRuntimeDefinition definition) {
        if (definition == null) {
            return responseProperties.effectiveMaxInputCharacters();
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
