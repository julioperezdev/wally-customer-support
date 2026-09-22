package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.application.tool.ConversationRouteToolContract;
import com.wally.customersupport.conversation.application.tool.WcsToolDescriptor;
import com.wally.customersupport.conversation.application.port.out.ConversationIntentClassifier;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.conversation.domain.model.CustomerPreference;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.ClasspathPromptRegistry;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptDefinition;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptRegistry;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptTemplateRenderer;
import com.wally.customersupport.shared.infrastructure.config.AiPromptProperties;
import com.wally.customersupport.shared.infrastructure.config.AiProperties;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wcs.ai.provider", havingValue = "bedrock")
@Slf4j
public class BedrockConversationIntentClassifier implements ConversationIntentClassifier {

    private static final double DEFAULT_GENERAL_SUPPORT_CONFIDENCE = 0.70;
    private static final double DEFAULT_SAFE_ROUTE_CONFIDENCE = 0.90;
    private static final Set<String> POLICY_KEYS = Set.of("shipping", "payments", "changes", "returns");

    private final BedrockConverseClient converseClient;
    private final ObjectMapper objectMapper;
    private final AiPromptProperties promptProperties;
    private final PromptDefinition prompt;
    private final AiProperties aiProperties;
    private final BedrockAgentProfileResolver profileResolver;

    public BedrockConversationIntentClassifier(BedrockConverseClient converseClient, ObjectMapper objectMapper) {
        this(
                converseClient,
                objectMapper,
                new AiPromptProperties("conversation-intent-v4", 2_048, BigDecimal.ZERO, 2_000, 12),
                new ClasspathPromptRegistry(),
                new AiProperties("bedrock", null, "us-east-1", null, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    public BedrockConversationIntentClassifier(
            BedrockConverseClient converseClient,
            ObjectMapper objectMapper,
            AiPromptProperties promptProperties,
            PromptRegistry promptRegistry) {
        this(converseClient, objectMapper, promptProperties, promptRegistry,
                new AiProperties("bedrock", null, "us-east-1", null, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    public BedrockConversationIntentClassifier(
            BedrockConverseClient converseClient,
            ObjectMapper objectMapper,
            AiPromptProperties promptProperties,
            PromptRegistry promptRegistry,
            AiProperties aiProperties) {
        this(converseClient, objectMapper, promptProperties, promptRegistry, aiProperties, null);
    }

    @Autowired
    public BedrockConversationIntentClassifier(
            BedrockConverseClient converseClient,
            ObjectMapper objectMapper,
            AiPromptProperties promptProperties,
            PromptRegistry promptRegistry,
            AiProperties aiProperties,
            BedrockAgentProfileResolver profileResolver) {
        this.converseClient = converseClient;
        this.objectMapper = objectMapper;
        this.promptProperties = promptProperties;
        this.prompt = promptRegistry.intentPrompt(promptProperties.effectiveIntentVersion());
        this.aiProperties = aiProperties;
        this.profileResolver = profileResolver;
    }

    @Override
    public ConversationIntentDecision classify(ConversationContext context) {
        String message = context == null ? null : context.latestMessage();
        if (message == null || message.isBlank()) {
            return ConversationIntentDecision.unknown();
        }
        try {
            String correlationId = context.conversationId() == null
                    ? null
                    : context.conversationId().toString();
            AgentRuntimeDefinition profile = resolveProfile(context);
            String userMessage = profile == null ? buildUserMessage(context) : buildUserMessage(context, profile);
            String baseSystemPrompt = profile == null
                    ? prompt.content()
                    : profile.invocationConfiguration().systemPrompt();
            String promptVersion = profile == null ? prompt.version() : profile.semanticVersion();
            String promptHash = profile == null ? prompt.sha256() : profile.systemPromptHash();
            int maxOutputTokens = profile == null
                    ? promptProperties.effectiveIntentMaxOutputTokens()
                    : profile.maxOutputTokens();
            float temperature = profile == null
                    ? promptProperties.effectiveIntentTemperature()
                    : profile.inferenceParameters().temperature().floatValue();
            boolean structuredToolCalling = profile == null
                    ? aiProperties.structuredToolCallingEnabled()
                    : profile.invocationConfiguration().structuredToolCalling();
            String output;
            if (structuredToolCalling) {
                String structuredSystemPrompt = profile == null
                        ? structuredToolUsePrompt()
                        : baseSystemPrompt + structuredToolSuffix();
                String structuredPromptHash = sha256(structuredSystemPrompt);
                WcsToolDescriptor routeContract = profile == null
                        ? ConversationRouteToolContract.DESCRIPTOR
                        : configuredRouteContract(profile);
                BedrockConverseClient.ToolUseCompletion completion;
                if (profile != null) {
                    completion = correlationId == null
                        ? converseClient.completeWithToolUseForAgent(
                                "intent-classification",
                                "conversation.intent.classify",
                                structuredSystemPrompt,
                                userMessage,
                                maxOutputTokens,
                                temperature,
                                profile.inferenceParameters().topP().floatValue(),
                                promptVersion,
                                structuredPromptHash,
                                routeContract,
                                profile)
                        : converseClient.completeWithToolUseForAgent(
                                "intent-classification",
                                "conversation.intent.classify",
                                structuredSystemPrompt,
                                userMessage,
                                maxOutputTokens,
                                temperature,
                                profile.inferenceParameters().topP().floatValue(),
                                promptVersion,
                                structuredPromptHash,
                                correlationId,
                                routeContract,
                                profile);
                } else {
                    completion = correlationId == null
                            ? converseClient.completeWithToolUseForRouter(
                                    "intent-classification", "conversation.intent.classify", structuredSystemPrompt,
                                    userMessage, maxOutputTokens, temperature, promptVersion, structuredPromptHash,
                                    routeContract)
                            : converseClient.completeWithToolUseForRouter(
                                    "intent-classification", "conversation.intent.classify", structuredSystemPrompt,
                                    userMessage, maxOutputTokens, temperature, promptVersion, structuredPromptHash,
                                    correlationId, routeContract);
                }
                output = completion.inputJson();
            } else {
                if (profile != null) {
                    output = correlationId == null
                            ? converseClient.completeForAgent(
                                    "intent-classification", "conversation.intent.classify", baseSystemPrompt,
                                    userMessage, maxOutputTokens, temperature,
                                    profile.inferenceParameters().topP().floatValue(), promptVersion,
                                    promptHash, profile)
                            : converseClient.completeForAgent(
                                    "intent-classification", "conversation.intent.classify", baseSystemPrompt,
                                    userMessage, maxOutputTokens, temperature,
                                    profile.inferenceParameters().topP().floatValue(), promptVersion,
                                    promptHash, profile, correlationId);
                } else {
                    output = correlationId == null
                        ? converseClient.completeForRouter(
                                "intent-classification",
                                "conversation.intent.classify",
                                baseSystemPrompt,
                                userMessage,
                                maxOutputTokens,
                                temperature,
                                promptVersion,
                                promptHash)
                        : converseClient.completeForRouter(
                                "intent-classification",
                                "conversation.intent.classify",
                                baseSystemPrompt,
                                userMessage,
                                maxOutputTokens,
                                temperature,
                                promptVersion,
                                promptHash,
                                correlationId);
                }
            }
            return parse(output, context);
        } catch (RuntimeException exception) {
            recordFallback("provider", exception, context);
            return ConversationIntentDecision.unknown();
        }
    }

    private String structuredToolUsePrompt() {
        return prompt.content() + structuredToolSuffix();
    }

    private String structuredToolSuffix() {
        return "\n\nMODO STRUCTURED TOOL USE:\n"
                + "Usa la herramienta conversation_route exactamente una vez para devolver la decision estructurada. "
                + "No escribas JSON como texto, no respondas con texto libre y no agregues una respuesta conversacional. "
                + "La herramienta es la unica salida valida para este turno.";
    }

    private AgentRuntimeDefinition resolveProfile(ConversationContext context) {
        return profileResolver == null
                ? null
                : profileResolver.resolve("conversation-router", "ROUTING", context).orElse(null);
    }

    private WcsToolDescriptor configuredRouteContract(AgentRuntimeDefinition definition) {
        return new WcsToolDescriptor(
                ConversationRouteToolContract.DESCRIPTOR.name(),
                ConversationRouteToolContract.DESCRIPTOR.description(),
                definition.outputSchemaVersion(),
                definition.invocationConfiguration().outputSchemaJson(),
                ConversationRouteToolContract.DESCRIPTOR.outputSchemaVersion(),
                ConversationRouteToolContract.DESCRIPTOR.outputSchemaJson(),
                ConversationRouteToolContract.DESCRIPTOR.requiredCapability());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                result.append(String.format("%02x", current));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private ConversationIntentDecision parse(String output, ConversationContext context) {
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(output));
            ConversationIntent intent = parseIntent(root.path("intent").asText(null));
            ConversationAction action = parseAction(root.path("action").asText(null), intent);
            double confidence = parseConfidence(root.path("confidence"), intent);
            CatalogQuery catalogQuery = action == ConversationAction.CATALOG_SEARCH
                    || action == ConversationAction.PURCHASE_LINK
                    || action.isCartOperation()
                    ? catalogQuery(root.path("catalogQuery"))
                    : null;
            String policyKey = intent == ConversationIntent.POLICY_QUERY
                    ? policyKey(root.path("policyKey").asText(null))
                    : null;
            return new ConversationIntentDecision(
                    intent,
                    action,
                    confidence,
                    catalogQuery,
                    policyKey,
                    parseQuantity(root.path("quantity")),
                    missingParameters(root.path("missingParameters")));
        } catch (RuntimeException exception) {
            recordFallback("parse", exception, context);
            return ConversationIntentDecision.unknown();
        }
    }

    private void recordFallback(String stage, RuntimeException exception, ConversationContext context) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("operation", "conversation.intent.classify");
        fields.put("stage", stage);
        fields.put("result", "UNKNOWN");
        fields.put("errorType", exception.getClass().getSimpleName());
        if (context != null && context.conversationId() != null) {
            fields.put("correlationId", context.conversationId());
        }
        StructuredEventLog.warn(log, "ROUTER_CLASSIFICATION_FALLBACK", fields);
    }

    private ConversationAction parseAction(String value, ConversationIntent intent) {
        if (value == null || value.isBlank()) {
            return ConversationAction.fromIntent(intent);
        }
        try {
            return ConversationAction.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return ConversationAction.UNKNOWN;
        }
    }

    private int parseQuantity(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isNumber()) {
            return 1;
        }
        return Math.max(1, Math.min(100, node.asInt()));
    }

    private List<String> missingParameters(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            String text = value.asText(null);
            if (text != null && text.matches("[a-zA-Z][a-zA-Z0-9_.-]{0,31}")) {
                values.add(text.toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

    private double parseConfidence(JsonNode node, ConversationIntent intent) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            // These routes are read-only, deterministic or explicitly safe:
            // allow a valid intent with omitted confidence to reach its bounded
            // handler. Operational routes still require explicit confidence.
            return safeDefaultConfidence(intent);
        }
        if (isSafeRoute(intent)) {
            try {
                double parsed = Double.parseDouble(node.asText());
                return parsed <= 0.0 ? safeDefaultConfidence(intent) : parsed;
            } catch (NumberFormatException exception) {
                return safeDefaultConfidence(intent);
            }
        }
        return node.isNumber() ? node.asDouble() : 0.0;
    }

    private boolean isSafeRoute(ConversationIntent intent) {
        return switch (intent) {
            case GREETING, BUSINESS_HOURS, POLICY_QUERY, GENERAL_SUPPORT, HUMAN_HANDOFF -> true;
            default -> false;
        };
    }

    private double safeDefaultConfidence(ConversationIntent intent) {
        return intent == ConversationIntent.GENERAL_SUPPORT
                ? DEFAULT_GENERAL_SUPPORT_CONFIDENCE
                : switch (intent) {
                    case GREETING, BUSINESS_HOURS, POLICY_QUERY, HUMAN_HANDOFF -> DEFAULT_SAFE_ROUTE_CONFIDENCE;
                    default -> 0.0;
                };
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
        if (context.selection() != null && context.selection().hasCatalogSelection()) {
            CatalogQuery selection = context.selection().catalogQuery();
            userPrompt.append("\n<active_selection>\n")
                    .append("stage=").append(context.selection().stage()).append("\n")
                    .append("intent=").append(context.selection().intent()).append("\n")
                    .append("action=").append(context.selection().action()).append("\n")
                    .append("catalogQuery=").append(selection).append("\n")
                    .append("</active_selection>\n")
                    .append("La selección activa es contexto auxiliar. Validá el turno actual y no inventes hechos.");
            if (context.selection().workingMemory().hasCandidates()) {
                userPrompt.append("\n<recent_catalog_candidates>\n");
                context.selection().workingMemory().catalogCandidates().forEach(candidate ->
                        userPrompt.append("sku=").append(candidate.sku())
                                .append("; product=").append(candidate.productName())
                                .append("; size=").append(candidate.size())
                                .append("; color=").append(candidate.color())
                                .append("\n"));
                userPrompt.append("</recent_catalog_candidates>\n")
                        .append("Las referencias como 'esa', 'el segundo' o 'una' sólo pueden resolverse hacia estas variantes; si no es único, pedí aclaración.");
            }
        }
        return userPrompt.toString();
    }

    private String buildUserMessage(ConversationContext context, AgentRuntimeDefinition definition) {
        String latestMessage = context.latestMessage();
        List<String> messages = new ArrayList<>(context.recentMessages().reversed());
        if (messages.isEmpty() || !latestMessage.equals(messages.getLast())) messages.add(latestMessage);
        int inputCharacterLimit = Math.min(12_000, Math.multiplyExact(definition.maxInputTokens(), 4));
        int historyStart = Math.max(0, messages.size() - 1 - promptProperties.effectiveMaxHistoryMessages());
        StringBuilder history = new StringBuilder();
        for (int index = historyStart; index < messages.size() - 1; index++) {
            history.append("<customer_message>\n")
                    .append(limit(messages.get(index), inputCharacterLimit))
                    .append("\n</customer_message>\n");
        }
        String summary = context.conversationSummary() == null || context.conversationSummary().isBlank()
                ? ""
                : "\n<conversation_summary>\n" + limit(context.conversationSummary(), inputCharacterLimit)
                        + "\n</conversation_summary>";
        String preferences = context.preferences().isEmpty()
                ? ""
                : "\n<customer_preferences>\n" + context.preferences().stream()
                        .map(preference -> preference.key() + "=" + preference.value())
                        .collect(java.util.stream.Collectors.joining("\n"))
                        + "\n</customer_preferences>\n"
                        + "Las preferencias son contexto auxiliar y nunca reemplazan filtros explícitos del turno actual.";
        String selection = selectionSection(context, inputCharacterLimit);
        return PromptTemplateRenderer.render(definition.invocationConfiguration().userPromptTemplate(), Map.of(
                "prompt_version", definition.systemPromptVersion(),
                "conversation_history", history.toString(),
                "latest_customer_message", limit(messages.getLast(), inputCharacterLimit),
                "conversation_summary_section", summary,
                "customer_preferences_section", preferences,
                "active_selection_section", selection));
    }

    private String selectionSection(ConversationContext context, int inputCharacterLimit) {
        if (context.selection() == null || !context.selection().hasCatalogSelection()) return "";
        StringBuilder selection = new StringBuilder("\n<active_selection>\n")
                .append("stage=").append(context.selection().stage()).append("\n")
                .append("intent=").append(context.selection().intent()).append("\n")
                .append("action=").append(context.selection().action()).append("\n")
                .append("catalogQuery=").append(context.selection().catalogQuery())
                .append("\n</active_selection>\n")
                .append("La selección activa es contexto auxiliar. Validá el turno actual y no inventes hechos.");
        if (context.selection().workingMemory().hasCandidates()) {
            selection.append("\n<recent_catalog_candidates>\n");
            context.selection().workingMemory().catalogCandidates().forEach(candidate ->
                    selection.append("sku=").append(candidate.sku())
                            .append("; product=").append(candidate.productName())
                            .append("; size=").append(candidate.size())
                            .append("; color=").append(candidate.color()).append("\n"));
            selection.append("</recent_catalog_candidates>\n")
                    .append("Las referencias como 'esa', 'el segundo' o 'una' sólo pueden resolverse hacia estas variantes; si no es único, pedí aclaración.");
        }
        return limit(selection.toString(), inputCharacterLimit);
    }

    private static String limit(String value, int maximum) {
        return value.substring(0, Math.min(value.length(), maximum));
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
