package com.wally.customersupport.catalog.application.service;

import java.util.List;
import java.util.Optional;

import com.wally.customersupport.catalog.domain.model.CatalogProduct;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Executes the deterministic catalog use case from structured filters.
 *
 * <p>The structured {@link CatalogSearchResult} is the application boundary.
 * The legacy reply methods remain as a compatibility facade for channels
 * while callers migrate to facts plus an independent formatter.</p>
 */
@Service
@RequiredArgsConstructor
public class CatalogConversationService {

    private static final int MAX_GENERAL_RESULTS = 5;

    private final CatalogQueryService catalogQueryService;

    public Optional<String> replyFor(String message) {
        return CatalogQueryParser.parse(message)
                .flatMap(query -> replyFor(query, List.of(), message));
    }

    public Optional<String> replyFor(CatalogQuery query) {
        return replyFor(query, List.of(), null);
    }

    public Optional<String> replyFor(CatalogQuery query, List<String> recentMessages, String latestMessage) {
        return search(query, recentMessages, latestMessage).map(CatalogResponseFormatter::render);
    }

    public Optional<CatalogSearchResult> search(
            CatalogQuery query,
            List<String> recentMessages,
            String latestMessage) {
        if (query == null) {
            return Optional.of(clarification("QUERY_REQUIRED"));
        }
        CatalogQuery activeQuery = resolveActiveQuery(query, recentMessages, latestMessage);
        CatalogQueryParser.FollowUpKind followUpKind = CatalogQueryParser.followUpKind(latestMessage);
        if (followUpKind != CatalogQueryParser.FollowUpKind.NONE) {
            return Optional.of(searchFollowUp(activeQuery, followUpKind));
        }
        return Optional.of(searchQuery(activeQuery));
    }

    private CatalogSearchResult searchQuery(CatalogQuery query) {
        if (query.isEmpty()) {
            List<CatalogFact> generalFacts = facts(catalogQueryService.searchAll(MAX_GENERAL_RESULTS));
            return generalFacts.isEmpty()
                    ? noMatch()
                    : matched(generalFacts.stream().limit(MAX_GENERAL_RESULTS).toList());
        }
        List<CatalogFact> facts = facts(catalogQueryService.search(query));
        if (!facts.isEmpty()) {
            return matched(facts);
        }
        if (query.productType() != null) {
            List<CatalogFact> alternatives = facts(catalogQueryService.search(query.withoutProductType()));
            if (!alternatives.isEmpty()) {
                return new CatalogSearchResult(
                        CatalogSearchResult.Status.ALTERNATIVES,
                        alternatives,
                        query.productType(),
                        CatalogSearchResult.FollowUpKind.NONE,
                        "PRODUCT_TYPE_ALTERNATIVES");
            }
        }
        return noMatch();
    }

    private CatalogSearchResult searchFollowUp(
            CatalogQuery activeQuery,
            CatalogQueryParser.FollowUpKind followUpKind) {
        if (activeQuery == null || activeQuery.isEmpty()) {
            return clarification("FOLLOW_UP");
        }

        List<CatalogFact> matches = facts(catalogQueryService.search(activeQuery));
        if (matches.isEmpty()) {
            return noMatch();
        }
        if (matches.size() > 1) {
            return new CatalogSearchResult(
                    CatalogSearchResult.Status.AMBIGUOUS,
                    List.of(),
                    null,
                    CatalogSearchResult.FollowUpKind.NONE,
                    "MULTIPLE_VARIANTS");
        }
        return new CatalogSearchResult(
                CatalogSearchResult.Status.MATCHED,
                matches,
                null,
                toResultFollowUpKind(followUpKind),
                "FOLLOW_UP_MATCHED");
    }

    private static CatalogQuery resolveActiveQuery(
            CatalogQuery query,
            List<String> recentMessages,
            String latestMessage) {
        if (query != null && !query.isEmpty()) {
            return query;
        }
        if (CatalogQueryParser.followUpKind(latestMessage) == CatalogQueryParser.FollowUpKind.NONE) {
            return query;
        }
        return CatalogQueryParser.parseConversation(recentMessages, latestMessage)
                .filter(parsed -> !parsed.isEmpty())
                .orElse(query);
    }

    private static List<CatalogFact> facts(List<CatalogProduct> products) {
        if (products == null) {
            return List.of();
        }
        return products.stream()
                .flatMap(product -> product.variants().stream()
                        .map(variant -> CatalogFact.from(product, variant)))
                .toList();
    }

    private static CatalogSearchResult matched(List<CatalogFact> facts) {
        return new CatalogSearchResult(
                CatalogSearchResult.Status.MATCHED,
                facts,
                null,
                CatalogSearchResult.FollowUpKind.NONE,
                "MATCHED");
    }

    private static CatalogSearchResult clarification(String reason) {
        return new CatalogSearchResult(
                CatalogSearchResult.Status.CLARIFICATION,
                List.of(),
                null,
                CatalogSearchResult.FollowUpKind.NONE,
                reason);
    }

    private static CatalogSearchResult noMatch() {
        return new CatalogSearchResult(
                CatalogSearchResult.Status.NO_MATCH,
                List.of(),
                null,
                CatalogSearchResult.FollowUpKind.NONE,
                "NO_MATCH");
    }

    private static CatalogSearchResult.FollowUpKind toResultFollowUpKind(
            CatalogQueryParser.FollowUpKind followUpKind) {
        return switch (followUpKind) {
            case NONE -> CatalogSearchResult.FollowUpKind.NONE;
            case AVAILABILITY -> CatalogSearchResult.FollowUpKind.AVAILABILITY;
            case PRICE -> CatalogSearchResult.FollowUpKind.PRICE;
            case SIZE -> CatalogSearchResult.FollowUpKind.SIZE;
            case COLOR -> CatalogSearchResult.FollowUpKind.COLOR;
        };
    }
}
