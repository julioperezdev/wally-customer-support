package com.wally.customersupport.catalog.application.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        CatalogQuery effectiveQuery = CatalogQueryParser.isGeneralCatalogRequest(latestMessage)
                ? CatalogQuery.empty()
                : query;
        if (effectiveQuery == null) {
            return Optional.of(clarification("QUERY_REQUIRED"));
        }
        if (CatalogQueryParser.isUnsupportedCatalogCategory(latestMessage)) {
            CatalogQuery parsedLatest = CatalogQueryParser.parse(latestMessage).orElse(query);
            String requestedCategory = parsedLatest.name() == null ? query.name() : parsedLatest.name();
            return Optional.of(unsupportedCategory(requestedCategory));
        }
        CatalogQuery activeQuery = resolveActiveQuery(effectiveQuery, recentMessages, latestMessage);
        if (CatalogQueryParser.isRelativeCheaperContinuation(latestMessage)) {
            return Optional.of(searchCheaper(activeQuery));
        }
        CatalogQueryParser.FollowUpKind followUpKind = CatalogQueryParser.followUpKind(latestMessage);
        if (followUpKind != CatalogQueryParser.FollowUpKind.NONE) {
            return Optional.of(searchFollowUp(activeQuery, followUpKind));
        }
        return Optional.of(searchQuery(activeQuery));
    }

    /**
     * Searches exactly the supplied filters without reconstructing them from
     * conversation history. This is used by deterministic commands such as
     * cart mutations, where quantity and action words must never become part
     * of the product name.
     */
    public Optional<CatalogSearchResult> searchExact(CatalogQuery query) {
        if (query == null) {
            return Optional.of(clarification("QUERY_REQUIRED"));
        }
        return Optional.of(searchQuery(query));
    }

    private CatalogSearchResult searchCheaper(CatalogQuery activeQuery) {
        List<CatalogProduct> products = activeQuery == null || activeQuery.isEmpty()
                ? catalogQueryService.searchAll(MAX_GENERAL_RESULTS)
                : searchProducts(activeQuery);
        List<CatalogFact> facts = facts(products);
        if (facts.isEmpty()) {
            return noMatch();
        }
        BigDecimal cheapestPrice = facts.stream()
                .map(CatalogFact::price)
                .min(BigDecimal::compareTo)
                .orElseThrow();
        List<CatalogFact> cheapest = facts.stream()
                .filter(fact -> fact.price().compareTo(cheapestPrice) == 0)
                .toList();
        return new CatalogSearchResult(
                CatalogSearchResult.Status.MATCHED,
                cheapest,
                null,
                CatalogSearchResult.FollowUpKind.NONE,
                "CHEAPEST_MATCH",
                images(products));
    }

    private CatalogSearchResult searchQuery(CatalogQuery query) {
        if (query.isEmpty()) {
            List<CatalogProduct> products = catalogQueryService.searchAll(MAX_GENERAL_RESULTS);
            List<CatalogFact> generalFacts = facts(products);
            return generalFacts.isEmpty()
                    ? noMatch()
                    : matched(
                            generalFacts.stream().limit(MAX_GENERAL_RESULTS).toList(),
                            images(products));
        }
        List<CatalogProduct> products = searchProducts(query);
        List<CatalogFact> facts = facts(products);
        if (!facts.isEmpty()) {
            return matched(query, facts, images(products));
        }
        if (query.productType() != null) {
            List<CatalogProduct> alternativeProducts = catalogQueryService.search(query.withoutProductType());
            List<CatalogFact> alternatives = facts(alternativeProducts);
            if (!alternatives.isEmpty()) {
                return new CatalogSearchResult(
                        CatalogSearchResult.Status.ALTERNATIVES,
                        alternatives,
                        query.productType(),
                        CatalogSearchResult.FollowUpKind.NONE,
                        "PRODUCT_TYPE_ALTERNATIVES",
                        images(alternativeProducts));
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

        List<CatalogProduct> products = searchProducts(activeQuery);
        List<CatalogFact> matches = facts(products);
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
                "FOLLOW_UP_MATCHED",
                images(products));
    }

    private List<CatalogProduct> searchProducts(CatalogQuery query) {
        if (!CatalogQueryParser.isWarmthSelection(query)) {
            return catalogQueryService.search(query);
        }
        Map<String, CatalogProduct> productsById = new LinkedHashMap<>();
        for (String productType : List.of("buzo", "campera")) {
            catalogQueryService.search(query.withProductType(productType))
                    .forEach(product -> productsById.put(product.id().toString(), product));
        }
        return new ArrayList<>(productsById.values());
    }

    private static CatalogQuery resolveActiveQuery(
            CatalogQuery query,
            List<String> recentMessages,
            String latestMessage) {
        if (CatalogQueryParser.isGeneralCatalogRequest(latestMessage)
                && !CatalogQueryParser.isContextualContinuation(latestMessage)) {
            return CatalogQuery.empty();
        }
        Optional<CatalogQuery> deterministicQuery = CatalogQueryParser.parseConversation(
                recentMessages, latestMessage)
                .filter(parsed -> !parsed.isEmpty())
                .or(() -> CatalogQueryParser.parse(latestMessage)
                        .filter(parsed -> !parsed.isEmpty()));
        if (deterministicQuery.isPresent()) {
            // Explicit words in the current turn and its bounded catalog
            // context win over a model-proposed query. The model may still
            // contribute a non-conflicting product name.
            return CatalogQueryParser.reconcile(deterministicQuery.get(), query);
        }
        return query;
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

    private static List<CatalogImage> images(List<CatalogProduct> products) {
        if (products == null) {
            return List.of();
        }
        return products.stream()
                .filter(product -> product.imageObjectKey() != null && !product.imageObjectKey().isBlank())
                .flatMap(product -> product.variants().stream()
                        .map(variant -> new CatalogImage(variant.sku(), product.imageObjectKey())))
                .toList();
    }

    private static CatalogSearchResult matched(
            List<CatalogFact> facts,
            List<CatalogImage> images) {
        return matched("MATCHED", facts, images);
    }

    private static CatalogSearchResult matched(
            CatalogQuery query,
            List<CatalogFact> facts,
            List<CatalogImage> images) {
        return matched(
                CatalogQueryParser.isWarmthSelection(query) ? "WARMTH_MATCH" : "MATCHED",
                facts,
                images);
    }

    private static CatalogSearchResult matched(
            String reason,
            List<CatalogFact> facts,
            List<CatalogImage> images) {
        return new CatalogSearchResult(
                CatalogSearchResult.Status.MATCHED,
                facts,
                null,
                CatalogSearchResult.FollowUpKind.NONE,
                reason,
                images);
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

    private static CatalogSearchResult unsupportedCategory(String requestedCategory) {
        return new CatalogSearchResult(
                CatalogSearchResult.Status.UNSUPPORTED_CATEGORY,
                List.of(),
                requestedCategory,
                CatalogSearchResult.FollowUpKind.NONE,
                "UNSUPPORTED_CATEGORY");
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
