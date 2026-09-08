package com.wally.customersupport.conversation.application.service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.wally.customersupport.agent.application.service.AgentActivationKey;
import com.wally.customersupport.agent.application.service.AgentActivationResolution;
import com.wally.customersupport.agent.application.service.AgentActivationResolver;
import com.wally.customersupport.agent.application.service.CatalogSpecialistExecutionRequest;
import com.wally.customersupport.agent.application.service.CatalogSpecialistExecutionResult;
import com.wally.customersupport.agent.application.service.CatalogSpecialistExecutor;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinitionResolution;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinitionResolver;
import com.wally.customersupport.agent.application.service.AgentShadowRuntimeService;
import com.wally.customersupport.catalog.application.service.CatalogConversationService;
import com.wally.customersupport.conversation.application.port.out.ConversationIntentClassifier;
import com.wally.customersupport.knowledge.application.port.out.KnowledgeRetriever;
import com.wally.customersupport.conversation.application.port.out.LlmClient;
import com.wally.customersupport.conversation.application.port.out.ResponseHumanizer;
import com.wally.customersupport.support.application.service.SupportConfigurationQueryService;
import com.wally.customersupport.support.domain.model.BusinessHour;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionPlan;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionResult;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationRequest;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import com.wally.customersupport.knowledge.domain.model.KnowledgeChunk;
import com.wally.customersupport.knowledge.domain.model.KnowledgeQuery;
import com.wally.customersupport.support.domain.model.SupportPolicy;
import com.wally.customersupport.shared.infrastructure.config.AgentRuntimeProperties;
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
    private final AgentActivationResolver agentActivationResolver;
    private final AgentRuntimeDefinitionResolver agentRuntimeDefinitionResolver;
    private final AgentRuntimeProperties agentRuntimeProperties;
    private final CatalogSpecialistExecutor catalogSpecialistExecutor;
    private final ResponseHumanizer responseHumanizer;
    private final AgentShadowRuntimeService agentShadowRuntimeService;

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
        AgentActivationKey activationKey = resolveActivationKey(context, plan);
        AgentActivationResolution activation = resolveActivation(activationKey, plan);
        AgentRuntimeDefinitionResolution definition = resolveDefinition(activationKey);
        Map<String, Object> routeFields = new LinkedHashMap<>();
        routeFields.put("workflowVersion", plan.workflowVersion());
        routeFields.put("useCase", plan.useCase());
        routeFields.put("action", plan.action().name());
        routeFields.put("stepCount", plan.stepCount());
        routeFields.put("maxSteps", plan.maxSteps());
        routeFields.put("fallbackAllowed", plan.fallbackAllowed());
        addActivationFields(routeFields, activation);
        addDefinitionFields(routeFields, definition);
        if (decision != null) {
            routeFields.put("intent", decision.intent().name());
            routeFields.put("confidence", decision.confidence());
        }
        StructuredEventLog.info(log, "AGENT_ROUTED", routeFields);
        Map<String, Object> startedFields = new LinkedHashMap<>();
        startedFields.put("workflowVersion", plan.workflowVersion());
        startedFields.put("useCase", plan.useCase());
        startedFields.put("stepCount", plan.stepCount());
        if (activation != null) {
            addActivationFields(startedFields, activation);
        }
        addDefinitionFields(startedFields, definition);
        StructuredEventLog.info(log, "AGENT_EXECUTION_STARTED", startedFields);

        ConversationExecutionResult result;
        try {
            String reply = switch (plan.action()) {
                case DIRECT_RESPONSE -> GREETING;
                case CATALOG_SEARCH -> executeCatalogSearch(context, decision, definition);
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
        runShadowSafely(definition, context, plan.useCase());
        return completeQuery(context, result, startedAt);
    }

    private void runShadowSafely(
            AgentRuntimeDefinitionResolution definition,
            ConversationContext context,
            String useCase) {
        try {
            agentShadowRuntimeService.executeIfEnabled(definition, context, useCase);
        } catch (RuntimeException exception) {
            // Candidate evidence must never break the active customer response.
            StructuredEventLog.warn(log, "AGENT_SHADOW_EXECUTION_FAILED", Map.of(
                    "useCase", useCase,
                    "errorType", exception.getClass().getSimpleName()));
        }
    }

    private String executeCatalogSearch(
            ConversationContext context,
            ConversationIntentDecision decision,
            AgentRuntimeDefinitionResolution definition) {
        if (definition != null && definition.isActive()) {
            CatalogSpecialistExecutionResult specialistResult = catalogSpecialistExecutor.execute(
                    new CatalogSpecialistExecutionRequest(
                            definition.definition(),
                            decision == null ? null : decision.catalogQuery(),
                            context.recentMessages(),
                            context.latestMessage()));
            if (specialistResult.executed()) {
                ResponseHumanizationResult humanized = responseHumanizer.humanize(
                        new ResponseHumanizationRequest(
                                "CATALOG_SEARCH",
                                context.channel(),
                                specialistResult.result()));
                return humanized == null ? SAFE_FALLBACK : humanized.text();
            }
        }
        return catalogConversationService.replyFor(
                        decision == null ? null : decision.catalogQuery(),
                        context.recentMessages(),
                        context.latestMessage())
                .orElse(LOW_CONFIDENCE);
    }

    private AgentActivationKey resolveActivationKey(
            ConversationContext context,
            ConversationExecutionPlan plan) {
        if (!agentRuntimeProperties.activationEnabled()) {
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
                agentRuntimeProperties.effectiveEnvironment(),
                channel,
                plan.useCase());
    }

    private AgentActivationResolution resolveActivation(
            AgentActivationKey key,
            ConversationExecutionPlan plan) {
        if (key == null) {
            return null;
        }
        AgentActivationResolution resolution = agentActivationResolver.resolve(key);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("useCase", plan.useCase());
        fields.put("channel", key.channel());
        addActivationFields(fields, resolution);
        StructuredEventLog.info(log, "AGENT_ACTIVATION_RESOLVED", fields);
        return resolution;
    }

    private AgentRuntimeDefinitionResolution resolveDefinition(AgentActivationKey key) {
        if (key == null) {
            return null;
        }
        return agentRuntimeDefinitionResolver.resolve(key);
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

    private static void addDefinitionFields(
            Map<String, Object> fields,
            AgentRuntimeDefinitionResolution definition) {
        if (definition == null) {
            return;
        }
        fields.put("definitionStatus", definition.status().name());
        fields.put("definitionReason", definition.reason().name());
        if (definition.isActive()) {
            fields.put("agentId", definition.definition().agentId());
            fields.put("agentVersion", definition.definition().agentVersion());
            fields.put("modelProvider", definition.definition().modelProvider());
            fields.put("model", definition.definition().modelId());
        }
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
                    context.preferences(),
                    context.channel()));
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
