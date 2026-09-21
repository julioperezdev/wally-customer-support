package com.wally.customersupport.conversation.application.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.wally.customersupport.catalog.application.service.CatalogQueryParser;
import com.wally.customersupport.catalog.application.service.CatalogQueryReconciler;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.conversation.domain.model.ConversationSelection;
import org.springframework.stereotype.Service;

/**
 * Converts an untrusted classifier proposal into a safe WCS decision.
 *
 * <p>This is the deterministic half of routing. It owns precedence between
 * the current turn, persisted selection and model proposal, while execution
 * remains in the application use cases.</p>
 */
@Service
public final class ConversationDecisionReconciler {

    private final ConversationDeterministicSignalResolver deterministicSignalResolver =
            new ConversationDeterministicSignalResolver();

    public ReconciliationResult reconcile(
            ConversationContext context,
            ConversationIntentDecision raw) {
        if (raw == null) {
            return new ReconciliationResult(
                    ConversationIntentDecision.unknown(),
                    "SAFE_FALLBACK",
                    List.of(),
                    List.of(),
                    "NULL_CLASSIFIER_DECISION");
        }

        if (context != null && CatalogQueryParser.isPurchaseRequest(context.latestMessage())) {
            ConversationIntentDecision purchase = purchaseDecision(context, raw);
            return result(purchase, "DETERMINISTIC_PURCHASE", "EXPLICIT_PURCHASE_MARKER");
        }

        Optional<ConversationIntentDecision> deterministicSignal = deterministicSignalResolver.resolve(context)
                .map(signal -> signal.intent() == ConversationIntent.CATALOG_SEARCH
                        ? normalizeCatalogDecision(context, signal)
                        : signal);
        if (deterministicSignal.isPresent() && shouldPreferDeterministicSignal(raw, deterministicSignal.get())) {
            return result(
                    deterministicSignal.get(),
                    "DETERMINISTIC_MESSAGE_SIGNAL",
                    "EXPLICIT_MESSAGE_SIGNAL");
        }

        ConversationIntentDecision normalized = normalizeCatalogDecision(context, raw);
        String strategy = normalized.equals(raw) ? "MODEL_PROPOSAL" : "DETERMINISTIC_RECONCILIATION";
        if (normalized.intent() == ConversationIntent.UNKNOWN) {
            strategy = "SAFE_FALLBACK";
        }
        return result(normalized, strategy, "CURRENT_TURN_AND_SELECTION_RECONCILIATION");
    }

    private static boolean shouldPreferDeterministicSignal(
            ConversationIntentDecision raw,
            ConversationIntentDecision deterministic) {
        if (raw == null) {
            return true;
        }
        // Cart and checkout operations retain the model action because the cart
        // boundary validates their command and required parameters separately.
        if (raw.action().isCartOperation() || raw.intent() == ConversationIntent.PURCHASE_LINK) {
            return false;
        }
        if (raw.intent() == ConversationIntent.UNKNOWN || raw.confidence() < 0.65) {
            return true;
        }
        if (raw.intent() != deterministic.intent()) {
            return true;
        }
        return raw.intent() == ConversationIntent.POLICY_QUERY
                && (raw.policyKey() == null || raw.policyKey().isBlank());
    }

    private ConversationIntentDecision purchaseDecision(
            ConversationContext context,
            ConversationIntentDecision raw) {
        CatalogQuery query = CatalogQueryParser.parsePurchaseConversation(
                        context.recentMessages(), context.latestMessage())
                .or(() -> activeSelection(context)
                        .map(ConversationSelection::catalogQuery))
                .orElseGet(() -> CatalogQueryReconciler.normalizeModelQuery(raw.catalogQuery()));
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

        if (catalogIntent && raw.catalogQuery() == null && deterministic.isEmpty()
                && !generalCatalog && !catalogFollowUp && !catalogRefinement && !unsupportedCategory) {
            return raw;
        }

        if (!generalCatalog && deterministic.isEmpty() && !catalogFollowUp && !catalogRefinement
                && !unsupportedCategory && !catalogIntent) {
            return raw;
        }

        CatalogQuery proposed = CatalogQueryReconciler.normalizeModelQuery(raw.catalogQuery());
        CatalogQuery query = deterministic
                .map(value -> CatalogQueryReconciler.reconcile(value, proposed))
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
        CatalogQuery proposed = CatalogQueryReconciler.normalizeModelQuery(raw.catalogQuery());
        if (deterministic.isEmpty() && proposed == null) {
            return raw;
        }
        CatalogQuery query = deterministic
                .map(value -> CatalogQueryReconciler.reconcile(value, proposed))
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

        if (!selection.isEmpty() && (isRefinementTurn(latest)
                || CatalogQueryParser.followUpKind(latest) != CatalogQueryParser.FollowUpKind.NONE)) {
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

    private static ReconciliationResult result(
            ConversationIntentDecision decision,
            String strategy,
            String reason) {
        return new ReconciliationResult(
                decision,
                strategy,
                resolvedFields(decision),
                decision.missingParameters(),
                reason);
    }

    public record ReconciliationResult(
            ConversationIntentDecision decision,
            String strategy,
            List<String> resolvedFields,
            List<String> missingFields,
            String reason) {

        public ReconciliationResult {
            decision = decision == null ? ConversationIntentDecision.unknown() : decision;
            strategy = strategy == null || strategy.isBlank() ? "UNKNOWN" : strategy;
            resolvedFields = resolvedFields == null ? List.of() : List.copyOf(resolvedFields);
            missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
            reason = reason == null || reason.isBlank() ? "UNSPECIFIED" : reason;
        }
    }
}
