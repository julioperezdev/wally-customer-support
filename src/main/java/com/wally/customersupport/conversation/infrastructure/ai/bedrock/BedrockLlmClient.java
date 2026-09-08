package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import java.util.stream.Collectors;

import com.wally.customersupport.conversation.application.port.out.LlmClient;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.ClasspathPromptRegistry;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptDefinition;
import com.wally.customersupport.shared.infrastructure.config.AiResponseProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wcs.ai.provider", havingValue = "bedrock")
public class BedrockLlmClient implements LlmClient {

    private final BedrockConverseClient converseClient;
    private final AiResponseProperties responseProperties;
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
            ClasspathPromptRegistry promptRegistry) {
        this.converseClient = converseClient;
        this.responseProperties = responseProperties;
        this.prompt = promptRegistry.responsePrompt(responseProperties.effectivePromptVersion());
    }

    @Override
    public String generateReply(ConversationContext context) {
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
                limit(context.latestMessage(), responseProperties.effectiveMaxInputCharacters()),
                context.recentMessages().stream()
                        .skip(Math.max(0, context.recentMessages().size()
                                - responseProperties.effectiveMaxHistoryMessages()))
                        .map(value -> limit(value, responseProperties.effectiveMaxInputCharacters()))
                        .collect(Collectors.joining("\n")),
                limit(context.conversationSummary(), responseProperties.effectiveMaxSummaryCharacters()),
                limit(context.preferences().stream()
                        .map(preference -> preference.key() + "=" + preference.value())
                        .collect(Collectors.joining("\n")), 1_000),
                limit(context.knowledge().stream()
                        .map(chunk -> "[" + chunk.sourceId() + "] " + chunk.content())
                        .collect(Collectors.joining("\n")), responseProperties.effectiveMaxKnowledgeCharacters()));
        return converseClient.complete(
                "response-generation",
                "conversation.reply.generate",
                prompt.content(),
                userPrompt,
                responseProperties.effectiveMaxOutputTokens(),
                responseProperties.effectiveTemperature(),
                prompt.version(),
                prompt.sha256());
    }

    private static String limit(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }
}
