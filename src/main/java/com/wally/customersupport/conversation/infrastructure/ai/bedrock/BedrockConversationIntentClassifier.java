package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.application.port.out.ConversationIntentClassifier;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.conversation.domain.model.CustomerPreference;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.ClasspathPromptRegistry;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptDefinition;
import com.wally.customersupport.shared.infrastructure.config.AiPromptProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wcs.ai.provider", havingValue = "bedrock")
public class BedrockConversationIntentClassifier implements ConversationIntentClassifier {

    private static final double DEFAULT_GENERAL_SUPPORT_CONFIDENCE = 0.70;
    private static final Set<String> POLICY_KEYS = Set.of("shipping", "payments", "changes", "returns");

    private final BedrockConverseClient converseClient;
    private final ObjectMapper objectMapper;
    private final AiPromptProperties promptProperties;
    private final PromptDefinition prompt;

    public BedrockConversationIntentClassifier(BedrockConverseClient converseClient, ObjectMapper objectMapper) {
        this(
                converseClient,
                objectMapper,
                new AiPromptProperties("conversation-intent-v1", 1_024, BigDecimal.ZERO, 2_000, 12),
                new ClasspathPromptRegistry());
    }

    @Autowired
    public BedrockConversationIntentClassifier(
            BedrockConverseClient converseClient,
            ObjectMapper objectMapper,
            AiPromptProperties promptProperties,
            ClasspathPromptRegistry promptRegistry) {
        this.converseClient = converseClient;
        this.objectMapper = objectMapper;
        this.promptProperties = promptProperties;
        this.prompt = promptRegistry.intentPrompt(promptProperties.effectiveIntentVersion());
    }

    @Override
    public ConversationIntentDecision classify(ConversationContext context) {
        String message = context == null ? null : context.latestMessage();
        if (message == null || message.isBlank()) {
            return ConversationIntentDecision.unknown();
        }
        try {
            String output = converseClient.complete(
                    "intent-classification",
                    "conversation.intent.classify",
                    prompt.content(),
                    buildUserMessage(context),
                    promptProperties.effectiveIntentMaxOutputTokens(),
                    promptProperties.effectiveIntentTemperature(),
                    prompt.version(),
                    prompt.sha256());
            return parse(output);
        } catch (RuntimeException exception) {
            return ConversationIntentDecision.unknown();
        }
    }

    private ConversationIntentDecision parse(String output) {
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(output));
            ConversationIntent intent = parseIntent(root.path("intent").asText(null));
            double confidence = parseConfidence(root.path("confidence"), intent);
            CatalogQuery catalogQuery = intent == ConversationIntent.CATALOG_SEARCH
                    ? catalogQuery(root.path("catalogQuery"))
                    : null;
            String policyKey = intent == ConversationIntent.POLICY_QUERY
                    ? policyKey(root.path("policyKey").asText(null))
                    : null;
            return new ConversationIntentDecision(intent, confidence, catalogQuery, policyKey);
        } catch (RuntimeException exception) {
            return ConversationIntentDecision.unknown();
        }
    }

    private double parseConfidence(JsonNode node, ConversationIntent intent) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            // GENERAL_SUPPORT is a read-only/documentary path. Allow a valid
            // intent with an omitted confidence to reach RAG, while keeping
            // malformed confidence unsafe for operational intents.
            return intent == ConversationIntent.GENERAL_SUPPORT
                    ? DEFAULT_GENERAL_SUPPORT_CONFIDENCE
                    : 0.0;
        }
        return node.isNumber() ? node.asDouble() : 0.0;
    }

    private CatalogQuery catalogQuery(JsonNode node) {
        if (node == null || !node.isObject()) {
            return new CatalogQuery(null, null, null, null);
        }
        return new CatalogQuery(textOrNull(node, "name"), textOrNull(node, "sku"),
                textOrNull(node, "size"), textOrNull(node, "color"), textOrNull(node, "productType"),
                decimalOrNull(node, "minPrice"), decimalOrNull(node, "maxPrice"));
    }

    private BigDecimal decimalOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText(null) == null
                || value.asText().isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText().trim().replace(',', '.'));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String buildUserMessage(ConversationContext context) {
        String latestMessage = context.latestMessage();
        List<String> messages = new ArrayList<>(context.recentMessages().reversed());
        if (messages.isEmpty() || !latestMessage.equals(messages.getLast())) {
            messages.add(latestMessage);
        }

        StringBuilder userPrompt = new StringBuilder("Version de prompt: ")
                .append(prompt.version())
                .append("\n<conversation_history>\n");
        int historyStart = Math.max(0, messages.size() - 1 - promptProperties.effectiveMaxHistoryMessages());
        for (int index = historyStart; index < messages.size() - 1; index++) {
            userPrompt.append("<customer_message>\n")
                    .append(limit(messages.get(index)))
                    .append("\n</customer_message>\n");
        }
        userPrompt.append("</conversation_history>\n<latest_customer_message>\n")
                .append(limit(messages.getLast()))
                .append("\n</latest_customer_message>");
        if (context.conversationSummary() != null && !context.conversationSummary().isBlank()) {
            userPrompt.append("\n<conversation_summary>\n")
                    .append(limit(context.conversationSummary()))
                    .append("\n</conversation_summary>");
        }
        if (!context.preferences().isEmpty()) {
            userPrompt.append("\n<customer_preferences>\n");
            for (CustomerPreference preference : context.preferences()) {
                userPrompt.append(preference.key()).append("=").append(preference.value()).append("\n");
            }
            userPrompt.append("</customer_preferences>\n");
            userPrompt.append("Las preferencias son contexto auxiliar y nunca reemplazan filtros explícitos del turno actual.");
        }
        return userPrompt.toString();
    }

    private String limit(String message) {
        return message.substring(0, Math.min(message.length(), promptProperties.effectiveMaxInputCharacters()));
    }

    private String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        return "size".equals(field)
                ? value.trim().toUpperCase(Locale.ROOT)
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private ConversationIntent parseIntent(String value) {
        if (value == null) {
            return ConversationIntent.UNKNOWN;
        }
        try {
            return ConversationIntent.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ConversationIntent.UNKNOWN;
        }
    }

    private String policyKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return POLICY_KEYS.contains(normalized) ? normalized : null;
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Bedrock intent response did not contain JSON");
        }
        return text.substring(start, end + 1);
    }
}
