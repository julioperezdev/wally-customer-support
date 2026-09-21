package com.wally.customersupport.conversation.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Coordinates the two routing stages: an untrusted LLM/classifier proposal
 * and deterministic WCS reconciliation. It does not execute a use case.
 */
@Slf4j
@Service
public final class ConversationRoutingService {

    private final ConversationIntentRouter intentRouter;
    private final ConversationDecisionReconciler decisionReconciler;

    @Autowired
    public ConversationRoutingService(
            ConversationIntentRouter intentRouter,
            ConversationDecisionReconciler decisionReconciler) {
        this.intentRouter = intentRouter;
        this.decisionReconciler = decisionReconciler;
    }

    public RoutingResult route(ConversationContext context) {
        ConversationIntentDecision raw = intentRouter.classify(context);
        if (raw == null) {
            return fallback(ConversationIntentDecision.unknown(), "NULL_CLASSIFIER_DECISION");
        }

        ConversationDecisionReconciler.ReconciliationResult reconciliation =
                decisionReconciler.reconcile(context, raw);
        if (!raw.equals(reconciliation.decision())) {
            logNormalization(context, raw, reconciliation.decision(), reconciliation.reason());
        }
        return new RoutingResult(
                raw,
                reconciliation.decision(),
                reconciliation.strategy(),
                reconciliation.resolvedFields(),
                reconciliation.missingFields());
    }

    private RoutingResult fallback(ConversationIntentDecision raw, String reason) {
        StructuredEventLog.warn(log, "ROUTING_SAFE_FALLBACK", Map.of(
                "reason", reason,
                "rawIntent", raw.intent().name(),
                "rawAction", raw.action().name()));
        return new RoutingResult(raw, ConversationIntentDecision.unknown(),
                "SAFE_FALLBACK", List.of(), List.of());
    }

    private void logNormalization(
            ConversationContext context,
            ConversationIntentDecision from,
            ConversationIntentDecision to,
            String reason) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("fromIntent", from.intent().name());
        fields.put("fromAction", from.action().name());
        fields.put("fromConfidence", from.confidence());
        fields.put("toIntent", to.intent().name());
        fields.put("toAction", to.action().name());
        fields.put("toConfidence", to.confidence());
        fields.put("reason", reason);
        addQueryTelemetry(fields, "fromCatalog", from.catalogQuery());
        addQueryTelemetry(fields, "toCatalog", to.catalogQuery());
        if (context != null) {
            if (context.conversationId() != null) {
                fields.put("correlationId", context.conversationId());
            }
            if (context.channel() != null) {
                fields.put("channel", context.channel().name());
            }
        }
        StructuredEventLog.info(log, "INTENT_DETERMINISTIC_OVERRIDE", fields);
    }

    private static void addQueryTelemetry(
            Map<String, Object> fields,
            String prefix,
            CatalogQuery query) {
        if (query == null) {
            fields.put(prefix + "FilterCount", 0);
            fields.put(prefix + "Filters", List.of());
            return;
        }
        fields.put(prefix + "FilterCount", query.presentFieldCount());
        fields.put(prefix + "Filters", query.presentFieldNames());
        if (query.productType() != null) {
            fields.put(prefix + "ProductType", query.productType());
        }
    }

    public record RoutingResult(
            ConversationIntentDecision rawDecision,
            ConversationIntentDecision decision,
            String strategy,
            List<String> resolvedFields,
            List<String> missingFields) {

        public RoutingResult {
            rawDecision = rawDecision == null ? ConversationIntentDecision.unknown() : rawDecision;
            decision = decision == null ? ConversationIntentDecision.unknown() : decision;
            strategy = strategy == null || strategy.isBlank() ? "UNKNOWN" : strategy;
            resolvedFields = resolvedFields == null ? List.of() : List.copyOf(resolvedFields);
            missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        }

        public boolean normalized() {
            return !rawDecision.equals(decision);
        }
    }
}
