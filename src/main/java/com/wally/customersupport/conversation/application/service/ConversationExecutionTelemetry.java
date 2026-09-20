package com.wally.customersupport.conversation.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wally.customersupport.agent.application.service.AgentActivationResolution;
import com.wally.customersupport.agent.application.service.AgentExecutionTraceRecorder;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinitionResolution;
import com.wally.customersupport.catalog.application.service.CatalogSearchResult;
import com.wally.customersupport.catalog.application.service.CatalogQueryParser;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionPlan;
import com.wally.customersupport.conversation.domain.model.ConversationExecutionResult;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.shared.infrastructure.config.AgentRuntimeProperties;
import com.wally.customersupport.shared.infrastructure.observability.ActorKeyGenerator;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Centralizes sanitized execution telemetry without owning conversation
 * decisions or business responses.
 */
@Service
@Slf4j
public class ConversationExecutionTelemetry {

    private final ActorKeyGenerator actorKeyGenerator;
    private final AgentExecutionTraceRecorder agentExecutionTraceRecorder;
    private final AgentRuntimeProperties agentRuntimeProperties;

    @Autowired
    public ConversationExecutionTelemetry(
            ActorKeyGenerator actorKeyGenerator,
            AgentExecutionTraceRecorder agentExecutionTraceRecorder,
            AgentRuntimeProperties agentRuntimeProperties) {
        this.actorKeyGenerator = actorKeyGenerator;
        this.agentExecutionTraceRecorder = agentExecutionTraceRecorder;
        this.agentRuntimeProperties = agentRuntimeProperties;
    }

    public void logIntentClassified(
            ConversationContext context,
            ConversationRoutingService.RoutingResult routingResult,
            ConversationIntentDecision decision,
            long startedAt) {
        ConversationIntentDecision rawDecision = routingResult.rawDecision();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("intent", decision.intent().name());
        fields.put("action", decision.action().name());
        fields.put("confidence", decision.confidence());
        fields.put("confidenceBucket", confidenceBucket(decision.confidence()));
        fields.put("rawIntent", rawDecision.intent().name());
        fields.put("rawAction", rawDecision.action().name());
        fields.put("rawConfidence", rawDecision.confidence());
        fields.put("deterministicNormalization", routingResult.normalized());
        fields.put("routingStrategy", routingResult.strategy());
        fields.put("resolvedEntityCount", routingResult.resolvedFields().size());
        fields.put("resolvedEntityTypes", routingResult.resolvedFields());
        fields.put("routingMissingParameterCount", routingResult.missingFields().size());
        fields.put("historyMessageCount", context.recentMessages().size());
        addCatalogQueryFields(fields, "catalogQuery", decision.catalogQuery());
        fields.put("missingParameterCount", decision.missingParameters().size());
        fields.put("durationMs", elapsedMillis(startedAt));
        addConversationIdentity(fields, context);
        StructuredEventLog.info(log, "INTENT_CLASSIFIED", fields);
    }

    public void logAgentRouted(
            ConversationContext context,
            ConversationExecutionPlan plan,
            ConversationIntentDecision decision,
            AgentActivationResolution activation,
            AgentRuntimeDefinitionResolution definition) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("workflowVersion", plan.workflowVersion());
        fields.put("useCase", plan.useCase());
        fields.put("action", plan.action().name());
        fields.put("stepCount", plan.stepCount());
        fields.put("maxSteps", plan.maxSteps());
        fields.put("fallbackAllowed", plan.fallbackAllowed());
        addConversationIdentity(fields, context);
        addActivationFields(fields, activation);
        addDefinitionFields(fields, definition);
        if (decision != null) {
            fields.put("intent", decision.intent().name());
            fields.put("confidence", decision.confidence());
        }
        StructuredEventLog.info(log, "AGENT_ROUTED", fields);
    }

    public void logAgentExecutionStarted(
            ConversationContext context,
            ConversationExecutionPlan plan,
            AgentActivationResolution activation,
            AgentRuntimeDefinitionResolution definition) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("workflowVersion", plan.workflowVersion());
        fields.put("useCase", plan.useCase());
        fields.put("stepCount", plan.stepCount());
        addConversationIdentity(fields, context);
        addActivationFields(fields, activation);
        addDefinitionFields(fields, definition);
        StructuredEventLog.info(log, "AGENT_EXECUTION_STARTED", fields);
    }

    public void logAgentExecutionFailed(
            ConversationContext context,
            ConversationExecutionPlan plan,
            RuntimeException exception,
            long startedAt) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("workflowVersion", plan.workflowVersion());
        fields.put("useCase", plan.useCase());
        fields.put("errorType", exception.getClass().getSimpleName());
        fields.put("durationMs", elapsedMillis(startedAt));
        addConversationIdentity(fields, context);
        StructuredEventLog.warn(log, "AGENT_EXECUTION_FAILED", fields);
    }

    public void logCatalogSearchOutcome(
            ConversationContext context,
            ConversationIntentDecision decision,
            CatalogSearchResult result,
            String source,
            long executionDurationMs) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("source", source);
        fields.put("resultStatus", result == null ? "NO_RESULT" : result.status().name());
        fields.put("resultCount", result == null ? 0 : result.resultCount());
        fields.put("imageCount", result == null ? 0 : result.images().size());
        fields.put("followUpKind", result == null ? null : result.followUpKind().name());
        fields.put("resultReason", result == null ? "NO_RESULT" : result.reason());
        fields.put("executionDurationMs", executionDurationMs);
        fields.put("queryType", queryType(result));
        fields.put("contextualContinuation", context != null
                && CatalogQueryParser.isContextualContinuation(context.latestMessage()));
        fields.put("cheaperContinuation", context != null
                && CatalogQueryParser.isCheaperContinuation(context.latestMessage()));
        fields.put("relativeCheaperContinuation", context != null
                && CatalogQueryParser.isRelativeCheaperContinuation(context.latestMessage()));
        fields.put("explicitPriceFilter", decision != null
                && decision.catalogQuery() != null
                && (decision.catalogQuery().minPrice() != null
                || decision.catalogQuery().maxPrice() != null));
        fields.put("warmthSelection", decision != null
                && CatalogQueryParser.isWarmthSelection(decision.catalogQuery()));
        addCatalogQueryFields(fields, "query", decision == null ? null : decision.catalogQuery());
        addConversationIdentity(fields, context);
        StructuredEventLog.info(log, "CATALOG_SEARCH_COMPLETED", fields);
    }

    public void logPurchaseOutcome(ConversationContext context, String result) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("operation", "conversation.purchase-link");
        fields.put("result", result);
        addConversationIdentity(fields, context);
        StructuredEventLog.info(log, "CONVERSATIONAL_PURCHASE_LINK", fields);
    }

    public void logGeneralSupportFailure(ConversationContext context, RuntimeException exception) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("errorType", exception.getClass().getSimpleName());
        addCorrelationId(fields, context);
        StructuredEventLog.warn(log, "GENERAL_SUPPORT_FAILED", fields);
    }

    public ConversationExecutionResult complete(
            ConversationContext context,
            ConversationExecutionResult result,
            long startedAt,
            AgentRuntimeDefinitionResolution definitionResolution) {
        long durationMs = elapsedMillis(startedAt);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("queryType", result.useCase());
        fields.put("outcome", result.outcome());
        fields.put("workflowVersion", result.workflowVersion());
        fields.put("executionStepCount", result.stepCount());
        fields.put("responseGenerated", result.response() != null && !result.response().isBlank());
        fields.put("mediaRequested", result.mediaReference() != null);
        if (result.fallbackReason() != null) {
            fields.put("fallbackReason", result.fallbackReason());
        }
        fields.put("durationMs", durationMs);
        addConversationIdentity(fields, context);
        StructuredEventLog.info(log, "AGENT_EXECUTION_COMPLETED", fields);
        StructuredEventLog.info(log, "CONVERSATION_QUERY_COMPLETED", fields);
        agentExecutionTraceRecorder.record(
                context,
                result,
                definitionResolution == null || !definitionResolution.isActive()
                        ? null : definitionResolution.definition(),
                agentRuntimeProperties.effectiveEnvironment(),
                durationMs);
        return result;
    }

    public void addConversationIdentity(Map<String, Object> fields, ConversationContext context) {
        if (context == null) {
            return;
        }
        if (context.channel() != null) {
            fields.put("channel", context.channel().name());
        }
        addCorrelationId(fields, context);
        actorKeyGenerator.generate(context.channel(), context.externalCustomerId())
                .ifPresent(actorKey -> fields.put("actorKey", actorKey));
    }

    public void addActivationFields(Map<String, Object> fields, AgentActivationResolution activation) {
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

    public void addDefinitionFields(
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
            fields.put("promptVersion", definition.definition().systemPromptVersion());
            fields.put("promptHash", definition.definition().systemPromptHash());
            fields.put("inputSchemaVersion", definition.definition().inputSchemaVersion());
            fields.put("outputSchemaVersion", definition.definition().outputSchemaVersion());
            fields.put("maxInputTokens", definition.definition().maxInputTokens());
            fields.put("maxOutputTokens", definition.definition().maxOutputTokens());
            fields.put("timeoutMs", definition.definition().timeout().toMillis());
        }
    }

    public void addCatalogQueryFields(Map<String, Object> fields, String prefix, CatalogQuery query) {
        if (query == null) {
            fields.put(prefix + "Present", false);
            fields.put(prefix + "FilterCount", 0);
            fields.put(prefix + "Filters", List.of());
            return;
        }
        fields.put(prefix + "Present", !query.isEmpty());
        fields.put(prefix + "FilterCount", query.presentFieldCount());
        fields.put(prefix + "Filters", query.presentFieldNames());
        if (query.productType() != null) {
            fields.put(prefix + "ProductType", query.productType());
        }
    }

    public long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    public static String confidenceBucket(double confidence) {
        if (confidence < 0.5) {
            return "<0.50";
        }
        if (confidence < 0.65) {
            return "0.50-0.64";
        }
        if (confidence < 0.8) {
            return "0.65-0.79";
        }
        if (confidence < 0.95) {
            return "0.80-0.94";
        }
        return ">=0.95";
    }

    private static String queryType(CatalogSearchResult result) {
        if (result == null) {
            return "CATALOG_SEARCH";
        }
        if (result.status() == CatalogSearchResult.Status.UNSUPPORTED_CATEGORY) {
            return "UNSUPPORTED_CATALOG";
        }
        return switch (result.followUpKind()) {
            case AVAILABILITY -> "STOCK_QUERY";
            case PRICE -> "PRICE_QUERY";
            case SIZE -> "SIZE_QUERY";
            case COLOR -> "COLOR_QUERY";
            case NONE -> "CATALOG_SEARCH";
        };
    }

    private static void addCorrelationId(Map<String, Object> fields, ConversationContext context) {
        if (context != null && context.conversationId() != null) {
            fields.put("correlationId", context.conversationId());
        }
    }
}
