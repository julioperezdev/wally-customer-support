package com.wally.customersupport.conversation.application.service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.wally.customersupport.catalog.application.service.CatalogConversationService;
import com.wally.customersupport.conversation.application.port.out.ConversationIntentClassifier;
import com.wally.customersupport.knowledge.application.port.out.KnowledgeRetriever;
import com.wally.customersupport.conversation.application.port.out.LlmClient;
import com.wally.customersupport.support.application.service.SupportConfigurationQueryService;
import com.wally.customersupport.support.domain.model.BusinessHour;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionPlan;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionResult;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.knowledge.domain.model.KnowledgeChunk;
import com.wally.customersupport.knowledge.domain.model.KnowledgeQuery;
import com.wally.customersupport.support.domain.model.SupportPolicy;
import com.wally.customersupport.shared.infrastructure.config.RagProperties;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationOrchestrator {

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
    private final ConversationExecutionPlanFactory executionPlanFactory;

    public String replyFor(ConversationContext context) {
        long startedAt = System.nanoTime();
        if (context == null || context.latestMessage() == null || context.latestMessage().isBlank()) {
            return completeQuery(
                    context,
                    executionPlanFactory.safeFallback("INVALID_INPUT"),
                    SAFE_FALLBACK,
                    startedAt);
        }

        ConversationIntentDecision decision;
        try {
            decision = intentClassifier.classify(context);
        } catch (RuntimeException exception) {
            StructuredEventLog.warn(log, "INTENT_CLASSIFICATION_FAILED", Map.of(
                    "errorType", exception.getClass().getSimpleName(),
                    "durationMs", elapsedMillis(startedAt)));
            return executePlan(
                    context,
                    executionPlanFactory.classificationFailure("CLASSIFICATION_FAILED"),
                    startedAt);
        }
        if (decision == null) {
            StructuredEventLog.warn(log, "INTENT_CLASSIFICATION_FAILED", Map.of(
                    "errorType", "null_decision",
                    "durationMs", elapsedMillis(startedAt)));
            return executePlan(
                    context,
                    executionPlanFactory.safeFallback("NULL_DECISION"),
                    startedAt);
        }
        StructuredEventLog.info(log, "INTENT_CLASSIFIED", Map.of(
                "intent", decision.intent().name(),
                "confidence", decision.confidence(),
                "durationMs", elapsedMillis(startedAt)));
        return executePlan(context, executionPlanFactory.create(decision), decision, startedAt);
    }

    private String executePlan(
            ConversationContext context,
            ConversationExecutionPlan plan,
            long startedAt) {
        return executePlan(context, plan, null, startedAt);
    }

    private String executePlan(
            ConversationContext context,
            ConversationExecutionPlan plan,
            ConversationIntentDecision decision,
            long startedAt) {
        Map<String, Object> routeFields = new LinkedHashMap<>();
        routeFields.put("workflowVersion", plan.workflowVersion());
        routeFields.put("useCase", plan.useCase());
        routeFields.put("action", plan.action().name());
        routeFields.put("stepCount", plan.stepCount());
        routeFields.put("maxSteps", plan.maxSteps());
        routeFields.put("fallbackAllowed", plan.fallbackAllowed());
        if (decision != null) {
            routeFields.put("intent", decision.intent().name());
            routeFields.put("confidence", decision.confidence());
        }
        StructuredEventLog.info(log, "AGENT_ROUTED", routeFields);
        StructuredEventLog.info(log, "AGENT_EXECUTION_STARTED", Map.of(
                "workflowVersion", plan.workflowVersion(),
                "useCase", plan.useCase(),
                "stepCount", plan.stepCount()));

        ConversationExecutionResult result;
        try {
            String reply = switch (plan.action()) {
                case DIRECT_RESPONSE -> GREETING;
                case CATALOG_SEARCH -> catalogConversationService.replyFor(
                                decision == null ? null : decision.catalogQuery(),
                                context.recentMessages(),
                                context.latestMessage())
                        .orElse(LOW_CONFIDENCE);
                case BUSINESS_HOURS -> formatBusinessHours();
                case POLICY_QUERY -> formatPolicy(decision == null ? null : decision.policyKey());
                case HUMAN_HANDOFF -> HUMAN_HANDOFF;
                case GENERAL_SUPPORT -> safeGeneralSupport(context);
                case LOW_CONFIDENCE -> LOW_CONFIDENCE;
                case SAFE_FALLBACK -> SAFE_FALLBACK;
            };
            result = SAFE_FALLBACK.equals(reply)
                    ? ConversationExecutionResult.fallback(
                            plan,
                            reply,
                            plan.fallbackReason() == null ? "SAFE_GENERAL_SUPPORT_FALLBACK" : plan.fallbackReason())
                    : ConversationExecutionResult.completed(plan, reply);
        } catch (RuntimeException exception) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("workflowVersion", plan.workflowVersion());
            fields.put("useCase", plan.useCase());
            fields.put("errorType", exception.getClass().getSimpleName());
            fields.put("durationMs", elapsedMillis(startedAt));
            StructuredEventLog.warn(log, "AGENT_EXECUTION_FAILED", fields);
            ConversationExecutionPlan fallback = executionPlanFactory.safeFallback("EXECUTION_FAILED");
            result = ConversationExecutionResult.fallback(fallback, SAFE_FALLBACK, "EXECUTION_FAILED");
        }
        return completeQuery(context, result, startedAt);
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
                    knowledge,
                    context.conversationSummary(),
                    context.preferences()));
        } catch (RuntimeException exception) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("errorType", exception.getClass().getSimpleName());
            addCorrelationId(fields, context);
            StructuredEventLog.warn(log, "GENERAL_SUPPORT_FAILED", fields);
            return SAFE_FALLBACK;
        }
    }

    private String completeQuery(
            ConversationContext context,
            ConversationExecutionPlan plan,
            String reply,
            long startedAt) {
        return completeQuery(
                context,
                ConversationExecutionResult.completed(plan, reply),
                startedAt);
    }

    private String completeQuery(
            ConversationContext context,
            ConversationExecutionResult result,
            long startedAt) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("queryType", result.useCase());
        fields.put("outcome", result.outcome());
        fields.put("workflowVersion", result.workflowVersion());
        fields.put("executionStepCount", result.stepCount());
        fields.put("responseGenerated", result.response() != null && !result.response().isBlank());
        if (result.fallbackReason() != null) {
            fields.put("fallbackReason", result.fallbackReason());
        }
        fields.put("durationMs", elapsedMillis(startedAt));
        addCorrelationId(fields, context);
        StructuredEventLog.info(log, "AGENT_EXECUTION_COMPLETED", fields);
        StructuredEventLog.info(log, "CONVERSATION_QUERY_COMPLETED", fields);
        return result.response();
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
