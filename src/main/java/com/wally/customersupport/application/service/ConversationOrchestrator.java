package com.wally.customersupport.application.service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.wally.customersupport.application.port.out.ConversationIntentClassifier;
import com.wally.customersupport.application.port.out.KnowledgeRetriever;
import com.wally.customersupport.application.port.out.LlmClient;
import com.wally.customersupport.domain.model.BusinessHour;
import com.wally.customersupport.domain.model.ConversationContext;
import com.wally.customersupport.domain.model.ConversationIntent;
import com.wally.customersupport.domain.model.ConversationIntentDecision;
import com.wally.customersupport.domain.model.KnowledgeChunk;
import com.wally.customersupport.domain.model.KnowledgeQuery;
import com.wally.customersupport.domain.model.SupportPolicy;
import com.wally.customersupport.infrastructure.config.RagProperties;
import com.wally.customersupport.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConversationOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConversationOrchestrator.class);
    private static final double MIN_CONFIDENCE = 0.65;
    private static final String GREETING = "Hola, ¿cómo te puedo ayudar?";
    private static final String LOW_CONFIDENCE = "No estoy seguro de haber entendido tu consulta. "
            + "Podés preguntarme por productos, stock, horarios, envíos o cambios.";
    private static final String SAFE_FALLBACK = "No pude interpretar la consulta. "
            + "Podés preguntarme por productos, stock, horarios o políticas de la tienda.";
    private static final String HUMAN_HANDOFF = "Entiendo. Un agente revisará tu consulta con el contexto "
            + "de esta conversación dentro de las próximas 24 horas.";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<String> DAY_NAMES = List.of(
            "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo");

    private final ConversationIntentClassifier intentClassifier;
    private final CatalogConversationService catalogConversationService;
    private final SupportConfigurationQueryService supportConfigurationQueryService;
    private final KnowledgeRetriever knowledgeRetriever;
    private final LlmClient llmClient;
    private final RagProperties ragProperties;

    public String replyFor(ConversationContext context) {
        long startedAt = System.nanoTime();
        if (context == null || context.latestMessage() == null || context.latestMessage().isBlank()) {
            return completeQuery(context, "UNKNOWN", "INVALID_INPUT", SAFE_FALLBACK, startedAt);
        }

        ConversationIntentDecision decision;
        try {
            decision = intentClassifier.classify(context.latestMessage());
        } catch (RuntimeException exception) {
            StructuredEventLog.warn(LOGGER, "INTENT_CLASSIFICATION_FAILED", Map.of(
                    "errorType", exception.getClass().getSimpleName(),
                    "durationMs", elapsedMillis(startedAt)));
            return completeQuery(
                    context,
                    "UNKNOWN",
                    "CLASSIFICATION_FAILED",
                    safeGeneralSupport(context),
                    startedAt);
        }
        if (decision == null) {
            StructuredEventLog.warn(LOGGER, "INTENT_CLASSIFICATION_FAILED", Map.of(
                    "errorType", "null_decision",
                    "durationMs", elapsedMillis(startedAt)));
            return completeQuery(context, "UNKNOWN", "CLASSIFICATION_FAILED", SAFE_FALLBACK, startedAt);
        }
        StructuredEventLog.info(LOGGER, "INTENT_CLASSIFIED", Map.of(
                "intent", decision.intent().name(),
                "confidence", decision.confidence(),
                "durationMs", elapsedMillis(startedAt)));
        if (decision.confidence() < MIN_CONFIDENCE) {
            return completeQuery(
                    context,
                    decision.intent().name(),
                    "LOW_CONFIDENCE",
                    LOW_CONFIDENCE,
                    startedAt);
        }

        String reply = switch (decision.intent()) {
            case GREETING -> GREETING;
            case CATALOG_SEARCH -> catalogConversationService.replyFor(decision.catalogQuery())
                    .orElse(LOW_CONFIDENCE);
            case BUSINESS_HOURS -> formatBusinessHours();
            case POLICY_QUERY -> formatPolicy(decision.policyKey());
            case HUMAN_HANDOFF -> HUMAN_HANDOFF;
            case GENERAL_SUPPORT, UNKNOWN -> safeGeneralSupport(context);
        };
        return completeQuery(context, decision.intent().name(), "REPLIED", reply, startedAt);
    }

    private String safeGeneralSupport(ConversationContext context) {
        try {
            List<KnowledgeChunk> knowledge = knowledgeRetriever.retrieve(new KnowledgeQuery(
                    context.latestMessage(),
                    context.conversationId(),
                    Math.max(1, ragProperties.maxResults())));
            return llmClient.generateReply(new ConversationContext(
                    context.conversationId(),
                    context.externalCustomerId(),
                    context.latestMessage(),
                    context.recentMessages(),
                    knowledge));
        } catch (RuntimeException exception) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("errorType", exception.getClass().getSimpleName());
            addCorrelationId(fields, context);
            StructuredEventLog.warn(LOGGER, "GENERAL_SUPPORT_FAILED", fields);
            return SAFE_FALLBACK;
        }
    }

    private String completeQuery(
            ConversationContext context,
            String queryType,
            String outcome,
            String reply,
            long startedAt) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("queryType", queryType);
        fields.put("outcome", outcome);
        fields.put("responseGenerated", reply != null && !reply.isBlank());
        fields.put("durationMs", elapsedMillis(startedAt));
        addCorrelationId(fields, context);
        StructuredEventLog.info(LOGGER, "CONVERSATION_QUERY_COMPLETED", fields);
        return reply;
    }

    private static void addCorrelationId(Map<String, Object> fields, ConversationContext context) {
        if (context != null && context.conversationId() != null) {
            fields.put("correlationId", context.conversationId());
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String formatBusinessHours() {
        List<BusinessHour> hours = supportConfigurationQueryService.businessHours();
        if (hours.isEmpty()) {
            return "No tengo horarios publicados. Un agente puede confirmarlos por vos.";
        }

        StringBuilder response = new StringBuilder("Nuestro horario de atención es:\n");
        for (BusinessHour hour : hours) {
            String day = hour.dayOfWeek() >= 1 && hour.dayOfWeek() <= DAY_NAMES.size()
                    ? DAY_NAMES.get(hour.dayOfWeek() - 1)
                    : "día " + hour.dayOfWeek();
            response.append("- ").append(capitalize(day)).append(": ");
            if (hour.closed()) {
                response.append("cerrado");
            } else {
                response.append(hour.opensAt().format(TIME_FORMATTER))
                        .append(" a ")
                        .append(hour.closesAt().format(TIME_FORMATTER));
            }
            response.append("\n");
        }
        response.append("Zona horaria: ")
                .append(hours.getFirst().timezone().getId());
        return response.toString();
    }

    private String formatPolicy(String policyKey) {
        if (policyKey == null || policyKey.isBlank()) {
            return LOW_CONFIDENCE;
        }
        return supportConfigurationQueryService.activePolicy(policyKey)
                .map(this::formatPolicy)
                .orElse("No tengo una política publicada para esa consulta. "
                        + "Un agente puede confirmarla por vos.");
    }

    private String formatPolicy(SupportPolicy policy) {
        return policy.title() + ": " + policy.content();
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
