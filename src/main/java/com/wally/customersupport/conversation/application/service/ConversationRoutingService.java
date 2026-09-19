package com.wally.customersupport.conversation.application.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.wally.customersupport.catalog.application.service.CatalogQueryParser;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.application.port.out.ConversationIntentClassifier;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.conversation.domain.model.ConversationSelection;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.extern.slf4j.Slf4j;

/**
 * Single validation boundary between language interpretation and WCS use cases.
 *
 * <p>The classifier may be Bedrock or a deterministic mock, but neither is
 * allowed to decide what executes. This service reconciles the proposal with
 * lexical facts from the current turn and the persisted typed selection. It
 * never generates SQL and never resolves catalog facts.</p>
 */
@Slf4j
public final class ConversationRoutingService {

    private static final Set<String> CATEGORY_NAMES = Set.of("remera", "buzo", "campera", "abrigo");

    private final ConversationIntentClassifier classifier;

    public ConversationRoutingService(ConversationIntentClassifier classifier) {
        this.classifier = classifier;
    }

    public RoutingResult route(ConversationContext context) {
        ConversationIntentDecision raw = classifier.classify(context);
        if (raw == null) {
            return fallback(ConversationIntentDecision.unknown(), "NULL_CLASSIFIER_DECISION");
        }

        if (context != null && CatalogQueryParser.isPurchaseRequest(context.latestMessage())) {
            ConversationIntentDecision purchase = purchaseDecision(context, raw);
            logNormalization(context, raw, purchase, "EXPLICIT_PURCHASE_MARKER");
            return result(raw, purchase, "DETERMINISTIC_PURCHASE", List.of(), purchase.missingParameters());
        }

        ConversationIntentDecision normalized = normalizeCatalogDecision(context, raw);
        if (!normalized.equals(raw)) {
            logNormalization(context, raw, normalized, "CURRENT_TURN_AND_SELECTION_RECONCILIATION");
        }
        String strategy = normalized.equals(raw) ? "MODEL_PROPOSAL" : "DETERMINISTIC_RECONCILIATION";
        List<String> resolved = resolvedFields(normalized);
        if (normalized.intent() == ConversationIntent.UNKNOWN) {
            return result(raw, normalized, "SAFE_FALLBACK", resolved, normalized.missingParameters());
        }
        return result(raw, normalized, strategy, resolved, normalized.missingParameters());
    }

    private ConversationIntentDecision purchaseDecision(
            ConversationContext context,
            ConversationIntentDecision raw) {
        CatalogQuery query = CatalogQueryParser.parsePurchaseConversation(
                        context.recentMessages(), context.latestMessage())
                .orElseGet(() -> activeSelection(context)
                        .map(ConversationSelection::catalogQuery)
                        .orElse(null));
        if (query == null || query.isEmpty()) {
            query = normalizeModelQuery(raw.catalogQuery());
        }
        return new ConversationIntentDecision(
                ConversationIntent.PURCHASE_LINK,
                ConversationAction.PURCHASE_LINK,
                0.99,
                query,
                null,
                CatalogQueryParser.purchaseQuantity(context.latestMessage()),
                query == null || query.isEmpty() ? List.of("productVariant") : List.of());
    }

    private ConversationIntentDecision normalizeCatalogDecision(
            ConversationContext context,
            ConversationIntentDecision raw) {
        if (context == null || raw.action().isCartOperation()) {
            return normalizeStructuredCatalogQuery(context, raw);
        }

        String latest = context.latestMessage();
        boolean generalCatalog = CatalogQueryParser.isGeneralCatalogRequest(latest);
        boolean explicitPurchase = CatalogQueryParser.isPurchaseRequest(latest);
        if (explicitPurchase) {
            return raw;
        }

        Optional<CatalogQuery> deterministic = deterministicQuery(context);
        boolean catalogFollowUp = CatalogQueryParser.followUpKind(latest)
                != CatalogQueryParser.FollowUpKind.NONE;
        boolean catalogRefinement = CatalogQueryParser.isFilterOnlyRefinement(latest)
                || CatalogQueryParser.isContextualContinuation(latest);
        boolean unsupportedCategory = CatalogQueryParser.isUnsupportedCatalogCategory(latest);
        boolean catalogIntent = raw.intent() == ConversationIntent.CATALOG_SEARCH
                || raw.action() == ConversationAction.CATALOG_SEARCH;

        if (!generalCatalog && deterministic.isEmpty() && !catalogFollowUp && !catalogRefinement
                && !unsupportedCategory && !catalogIntent) {
            return raw;
        }

        CatalogQuery proposed = normalizeModelQuery(raw.catalogQuery());
        CatalogQuery query = deterministic
                .map(value -> CatalogQueryParser.reconcile(value, proposed))
                .orElse(proposed);

        if (generalCatalog) {
            query = CatalogQuery.empty();
        }
        if (query == null) {
            query = CatalogQuery.empty();
        }

        return new ConversationIntentDecision(
                ConversationIntent.CATALOG_SEARCH,
                ConversationAction.CATALOG_SEARCH,
                Math.max(0.99, raw.confidence()),
                query,
                null,
                raw.quantity(),
                raw.missingParameters());
    }

    private ConversationIntentDecision normalizeStructuredCatalogQuery(
            ConversationContext context,
            ConversationIntentDecision raw) {
        Optional<CatalogQuery> deterministic = context == null
                ? Optional.empty()
                : deterministicQuery(context);
        CatalogQuery proposed = normalizeModelQuery(raw.catalogQuery());
        if (deterministic.isEmpty() && proposed == null) {
            return raw;
        }
        CatalogQuery query = deterministic
                .map(value -> CatalogQueryParser.reconcile(value, proposed))
                .orElse(proposed);
        return copyWithQuery(raw, query == null ? CatalogQuery.empty() : query);
    }

    private Optional<CatalogQuery> deterministicQuery(ConversationContext context) {
        String latest = context.latestMessage();
        if (CatalogQueryParser.isGeneralCatalogRequest(latest)) {
            return Optional.of(CatalogQuery.empty());
        }

        Optional<CatalogQuery> parsedConversation = CatalogQueryParser.parseConversation(
                context.recentMessages(), latest).filter(query -> !query.isEmpty());
        CatalogQuery selection = activeSelection(context).map(ConversationSelection::catalogQuery)
                .orElse(CatalogQuery.empty());
        CatalogQuery currentTurn = CatalogQueryParser.parse(latest).orElse(CatalogQuery.empty());

        if (!selection.isEmpty() && (isRefinementTurn(latest) || CatalogQueryParser.followUpKind(latest)
                != CatalogQueryParser.FollowUpKind.NONE)) {
            CatalogQuery merged = selection.merge(currentTurn);
            if (!merged.isEmpty()) {
                return Optional.of(merged);
            }
        }
        if (parsedConversation.isPresent()) {
            CatalogQuery parsed = parsedConversation.get();
            if (!currentTurn.hasPrimarySelector() && !selection.isEmpty()) {
                parsed = selection.merge(parsed);
            }
            return Optional.of(parsed);
        }
        if (!selection.isEmpty() && isRefinementTurn(latest)) {
            return Optional.of(selection.merge(currentTurn));
        }
        if (!currentTurn.isEmpty()) {
            return Optional.of(currentTurn);
        }
        return Optional.empty();
    }

    private static boolean isRefinementTurn(String message) {
        return CatalogQueryParser.isFilterOnlyRefinement(message)
                || CatalogQueryParser.isContextualContinuation(message);
    }

    private static Optional<ConversationSelection> activeSelection(ConversationContext context) {
        if (context == null || context.selection() == null || !context.selection().hasCatalogSelection()) {
            return Optional.empty();
        }
        return Optional.of(context.selection());
    }

    private static ConversationIntentDecision copyWithQuery(
            ConversationIntentDecision decision,
            CatalogQuery query) {
        return new ConversationIntentDecision(
                decision.intent(),
                decision.action(),
                decision.confidence(),
                query,
                decision.policyKey(),
                decision.quantity(),
                decision.missingParameters());
    }

    private static CatalogQuery normalizeModelQuery(CatalogQuery proposed) {
        if (proposed == null) {
            return null;
        }
        if (proposed.productType() == null && proposed.name() != null) {
            String normalizedName = proposed.name().strip().toLowerCase(Locale.ROOT);
            if (CATEGORY_NAMES.contains(normalizedName)) {
                return new CatalogQuery(
                        null,
                        proposed.sku(),
                        proposed.size(),
                        proposed.color(),
                        normalizedName,
                        proposed.minPrice(),
                        proposed.maxPrice());
            }
        }
        return proposed;
    }

    private static List<String> resolvedFields(ConversationIntentDecision decision) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        if (decision.intent() != ConversationIntent.UNKNOWN) {
            fields.add("intent");
        }
        if (decision.action() != ConversationAction.UNKNOWN && decision.action() != ConversationAction.NONE) {
            fields.add("action");
        }
        if (decision.catalogQuery() != null) {
            fields.addAll(decision.catalogQuery().presentFieldNames());
        }
        if (decision.policyKey() != null) {
            fields.add("policyKey");
        }
        return List.copyOf(fields);
    }

    private RoutingResult fallback(ConversationIntentDecision raw, String reason) {
        StructuredEventLog.warn(log, "ROUTING_SAFE_FALLBACK", Map.of(
                "reason", reason,
                "rawIntent", raw.intent().name(),
                "rawAction", raw.action().name()));
        return result(raw, ConversationIntentDecision.unknown(), "SAFE_FALLBACK", List.of(), List.of());
    }

    private void logNormalization(
            ConversationContext context,
            ConversationIntentDecision from,
            ConversationIntentDecision to,
            String reason) {
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
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

    private static RoutingResult result(
            ConversationIntentDecision raw,
            ConversationIntentDecision decision,
            String strategy,
            List<String> resolvedFields,
            List<String> missingFields) {
        return new RoutingResult(raw, decision, strategy, resolvedFields, missingFields);
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
