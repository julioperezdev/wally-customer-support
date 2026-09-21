package com.wally.customersupport.catalog.application.service;

import java.util.List;
import java.util.Optional;

import com.wally.customersupport.catalog.domain.model.CatalogQuery;

/**
 * Reconstructs a bounded catalog selection from the current turn and inbound
 * conversation history.
 *
 * <p>This boundary deliberately does not know how a catalog is queried or how
 * a response is rendered. It only resolves continuity, refinements and the
 * catalog context needed by purchase commands.</p>
 */
public final class CatalogConversationQueryResolver {

    public Optional<CatalogQuery> parseConversation(List<String> recentMessages, String latestMessage) {
        if (latestMessage == null || latestMessage.isBlank()) {
            return Optional.empty();
        }
        List<String> boundedHistory = recentMessages == null ? List.of() : recentMessages;
        boolean continuation = CatalogQueryParser.isContextualContinuation(latestMessage);
        if (!looksLikeCatalogTurn(latestMessage) && !continuation) {
            return Optional.empty();
        }
        Optional<CatalogQuery> latestQuery = CatalogQueryParser.parse(latestMessage)
                .or(() -> CatalogQueryParser.parseRefinement(latestMessage));
        if (latestQuery.isEmpty()
                && (continuation || CatalogQueryParser.followUpKind(latestMessage)
                        != CatalogQueryParser.FollowUpKind.NONE)) {
            latestQuery = Optional.of(CatalogQuery.empty());
        }
        if (latestQuery.isEmpty()) {
            return Optional.empty();
        }

        CatalogQuery activeQuery = latestQuery.get();
        boolean skippedLatestFromHistory = false;
        for (String message : boundedHistory.reversed()) {
            if (!skippedLatestFromHistory && java.util.Objects.equals(message, latestMessage)) {
                skippedLatestFromHistory = true;
                continue;
            }
            Optional<CatalogQuery> parsed = CatalogQueryParser.parse(message);
            if (parsed.isEmpty()) {
                continue;
            }
            CatalogQuery candidate = parsed.get();
            if (startsNewProductSelection(activeQuery, candidate)) {
                break;
            }
            activeQuery = activeQuery.mergeMissing(candidate);
        }
        return Optional.of(activeQuery);
    }

    /**
     * Reconstructs the latest catalog selection for a purchase command. The
     * purchase turn may add filters, but cannot make checkout authoritative.
     */
    public Optional<CatalogQuery> parsePurchaseConversation(
            List<String> recentMessages,
            String latestMessage) {
        if (!CatalogQueryParser.isPurchaseRequest(latestMessage)) {
            return Optional.empty();
        }

        CatalogQuery latestQuery = parsePurchaseMessage(latestMessage).orElse(CatalogQuery.empty());
        CatalogQuery previousQuery = latestCatalogQuery(recentMessages, latestMessage);
        if (previousQuery == null || previousQuery.isEmpty()) {
            return latestQuery.isEmpty() ? Optional.empty() : Optional.of(latestQuery);
        }
        return Optional.of(previousQuery.merge(latestQuery));
    }

    /** Returns the requested quantity, defaulting to one for checkout. */
    public int purchaseQuantity(String message) {
        if (message == null || message.isBlank()) {
            return 1;
        }
        String normalized = CatalogQueryParser.normalize(message);
        java.util.regex.Matcher matcher = CatalogQueryParser.PURCHASE_QUANTITY_AFTER_VERB.matcher(normalized);
        if (!matcher.find()) {
            matcher = CatalogQueryParser.PURCHASE_QUANTITY_WITH_UNIT.matcher(normalized);
            if (!matcher.find()) {
                matcher = CatalogQueryParser.PURCHASE_QUANTITY_X.matcher(normalized);
                if (!matcher.find()) {
                    return 1;
                }
            }
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private Optional<CatalogQuery> parsePurchaseMessage(String message) {
        String normalized = CatalogQueryParser.normalize(message);
        normalized = CatalogQueryParser.PURCHASE_QUANTITY_AFTER_VERB.matcher(normalized).replaceAll(" ");
        normalized = CatalogQueryParser.PURCHASE_QUANTITY_WITH_UNIT.matcher(normalized).replaceAll(" ");
        normalized = CatalogQueryParser.PURCHASE_QUANTITY_X.matcher(normalized).replaceAll(" ");
        return CatalogQueryParser.parse(normalized);
    }

    private CatalogQuery latestCatalogQuery(List<String> recentMessages, String latestMessage) {
        if (recentMessages == null) {
            return null;
        }
        boolean skippedLatest = false;
        for (String message : recentMessages) {
            if (!skippedLatest && java.util.Objects.equals(message, latestMessage)) {
                skippedLatest = true;
                continue;
            }
            if (!looksLikeCatalogTurn(message) && !CatalogQueryParser.isContextualContinuation(message)) {
                continue;
            }
            Optional<CatalogQuery> query = parseConversation(recentMessages, message)
                    .filter(parsed -> !parsed.isEmpty());
            if (query.isPresent()) {
                return query.get();
            }
        }
        return null;
    }

    private boolean looksLikeCatalogTurn(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = CatalogQueryParser.normalize(message);
        return CatalogQueryParser.CATALOG_MARKER.matcher(normalized).find()
                || CatalogQueryParser.SKU.matcher(normalized).find()
                || CatalogQueryParser.SIZE.matcher(normalized).find()
                || CatalogQueryParser.COLOR.matcher(normalized).find()
                || CatalogQueryParser.REFINEMENT_MARKER.matcher(normalized).find()
                || !CatalogQueryParser.extractPriceRange(message).isEmpty()
                || CatalogQueryParser.followUpKind(normalized) != CatalogQueryParser.FollowUpKind.NONE;
    }

    private static boolean startsNewProductSelection(CatalogQuery activeQuery, CatalogQuery candidate) {
        return activeQuery.productType() != null
                && candidate.productType() != null
                && !activeQuery.productType().equalsIgnoreCase(candidate.productType());
    }
}
