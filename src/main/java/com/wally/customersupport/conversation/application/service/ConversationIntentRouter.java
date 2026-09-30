package com.wally.customersupport.conversation.application.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.application.port.out.ConversationIntentClassifier;
import com.wally.customersupport.conversation.application.port.out.TypeSafeUseCaseSelector;
import com.wally.customersupport.conversation.application.port.out.TypeSafeUseCaseSelector.TypeSafeSelection;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.shared.infrastructure.config.ConversationRoutingProperties;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Owns only the LLM/classifier call. It produces an untrusted proposal; it
 * does not merge state, choose a tool or execute a business operation.
 */
@Service
@Slf4j
public final class ConversationIntentRouter {

    private final ConversationIntentClassifier classifier;
    private final TypeSafeUseCaseSelector typeSafeSelector;
    private final ConversationRoutingProperties properties;

    @Autowired
    public ConversationIntentRouter(
            ConversationIntentClassifier classifier,
            TypeSafeUseCaseSelector typeSafeSelector,
            ConversationRoutingProperties properties) {
        this.classifier = classifier;
        this.typeSafeSelector = typeSafeSelector;
        this.properties = properties;
    }

    public ConversationIntentRouter(ConversationIntentClassifier classifier) {
        this(classifier, context -> TypeSafeSelection.failure("NOT_CONFIGURED"),
                ConversationRoutingProperties.defaults());
    }

    public ConversationIntentDecision classify(ConversationContext context) {
        if (!properties.typeSafeEnabled()) {
            return classifier.classify(context);
        }
        return properties.shadowMode()
                ? classifyShadow(context)
                : classifyActive(context);
    }

    private ConversationIntentDecision classifyShadow(ConversationContext context) {
        ConversationIntentDecision baseline = safeBaseline(context);
        long startedAt = System.nanoTime();
        TypeSafeSelection candidate = selectSafely(context);
        logTypeSafeResult(
                "ROUTING_SHADOW_EVALUATED",
                candidate.successful() ? "EVALUATED" : "FAILED",
                candidate.failureReason(),
                candidate.action(),
                candidate.confidence(),
                candidate.model(),
                candidate.inputTokens(),
                candidate.outputTokens(),
                elapsedMillis(startedAt),
                baseline == null ? null : baseline.intent(),
                baseline == null ? null : baseline.action());
        return baseline;
    }

    private ConversationIntentDecision classifyActive(ConversationContext context) {
        long startedAt = System.nanoTime();
        TypeSafeSelection selection = selectSafely(context);
        if (!selection.successful()) {
            return fallback(context, selection.failureReason(), selection, startedAt);
        }
        if (selection.confidence() < properties.minimumConfidence()) {
            return fallback(context, "LOW_CONFIDENCE", selection, startedAt);
        }

        ConversationIntent intent = intentFor(selection.action());
        Enrichment enrichment = enrich(context, selection.action());
        ConversationIntentDecision decision = new ConversationIntentDecision(
                intent,
                selection.action(),
                selection.confidence(),
                enrichment.catalogQuery(),
                enrichment.policyKey(),
                enrichment.quantity(),
                enrichment.missingParameters());
        logTypeSafeResult(
                "ROUTING_PROVIDER_EVALUATED",
                "ACTIVE_SELECTED",
                null,
                selection.action(),
                selection.confidence(),
                selection.model(),
                selection.inputTokens(),
                selection.outputTokens(),
                elapsedMillis(startedAt),
                null,
                null);
        return decision;
    }

    private ConversationIntentDecision fallback(
            ConversationContext context,
            String reason,
            TypeSafeSelection selection,
            long startedAt) {
        ConversationIntentDecision baseline = safeBaseline(context);
        logTypeSafeResult(
                "ROUTING_PROVIDER_EVALUATED",
                "FALLBACK",
                reason,
                selection.action(),
                selection.confidence(),
                selection.model(),
                selection.inputTokens(),
                selection.outputTokens(),
                elapsedMillis(startedAt),
                baseline == null ? null : baseline.intent(),
                baseline == null ? null : baseline.action());
        return baseline;
    }

    private TypeSafeSelection selectSafely(ConversationContext context) {
        try {
            TypeSafeSelection selection = typeSafeSelector.select(context);
            return selection == null ? TypeSafeSelection.failure("INVALID_RESPONSE") : selection;
        } catch (RuntimeException exception) {
            return TypeSafeSelection.failure("CLIENT_ERROR");
        }
    }

    private ConversationIntentDecision safeBaseline(ConversationContext context) {
        try {
            ConversationIntentDecision baseline = classifier.classify(context);
            return baseline == null ? ConversationIntentDecision.unknown() : baseline;
        } catch (RuntimeException exception) {
            return ConversationIntentDecision.unknown();
        }
    }

    private Enrichment enrich(ConversationContext context, ConversationAction selectedAction) {
        if (!requiresEnrichment(selectedAction)) {
            return Enrichment.empty();
        }
        ConversationIntentDecision extracted = safeBaseline(context);
        if (extracted == null || extracted.action() != selectedAction) {
            return Enrichment.empty();
        }
        return new Enrichment(
                extracted.catalogQuery(),
                selectedAction == ConversationAction.POLICY_QUERY ? extracted.policyKey() : null,
                extracted.quantity(),
                extracted.missingParameters());
    }

    private static boolean requiresEnrichment(ConversationAction action) {
        return switch (action) {
            case CATALOG_SEARCH, ADD_TO_CART, REMOVE_FROM_CART, PURCHASE_LINK,
                    REVIEW_CHECKOUT, CONFIRM_CHECKOUT, CANCEL_CHECKOUT, POLICY_QUERY -> true;
            default -> false;
        };
    }

    private static ConversationIntent intentFor(ConversationAction action) {
        return switch (action) {
            case GREETING -> ConversationIntent.GREETING;
            case CATALOG_SEARCH, ADD_TO_CART, VIEW_CART, REMOVE_FROM_CART, CLEAR_CART ->
                    ConversationIntent.CATALOG_SEARCH;
            case PURCHASE_LINK, REVIEW_CHECKOUT, CONFIRM_CHECKOUT, CANCEL_CHECKOUT ->
                    ConversationIntent.PURCHASE_LINK;
            case BUSINESS_HOURS -> ConversationIntent.BUSINESS_HOURS;
            case POLICY_QUERY -> ConversationIntent.POLICY_QUERY;
            case HUMAN_HANDOFF -> ConversationIntent.HUMAN_HANDOFF;
            case GENERAL_SUPPORT -> ConversationIntent.GENERAL_SUPPORT;
            case NONE, UNKNOWN -> ConversationIntent.UNKNOWN;
        };
    }

    private void logTypeSafeResult(
            String event,
            String outcome,
            String reason,
            ConversationAction candidateAction,
            double confidence,
            String model,
            Integer inputTokens,
            Integer outputTokens,
            long durationMs,
            ConversationIntent baselineIntent,
            ConversationAction baselineAction) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("provider", "typesafe");
        fields.put("mode", properties.mode());
        fields.put("outcome", outcome);
        fields.put("candidateAction", candidateAction == null ? null : candidateAction.name());
        fields.put("confidence", confidence);
        fields.put("model", model);
        fields.put("inputTokens", inputTokens);
        fields.put("outputTokens", outputTokens);
        fields.put("durationMs", durationMs);
        fields.put("fallbackReason", reason);
        fields.put("baselineIntent", baselineIntent == null ? null : baselineIntent.name());
        fields.put("baselineAction", baselineAction == null ? null : baselineAction.name());
        StructuredEventLog.info(log, event, fields);
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private record Enrichment(
            CatalogQuery catalogQuery,
            String policyKey,
            int quantity,
            List<String> missingParameters) {

        private Enrichment {
            missingParameters = missingParameters == null ? List.of() : List.copyOf(missingParameters);
        }

        private static Enrichment empty() {
            return new Enrichment(null, null, 1, List.of());
        }
    }
}
