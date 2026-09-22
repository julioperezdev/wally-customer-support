package com.wally.customersupport.conversation.application.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wally.customersupport.cart.application.service.CartCommandParser;
import com.wally.customersupport.catalog.application.service.CatalogQueryParser;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.CatalogCandidateReference;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationWorkingMemory;

/**
 * Resolves bounded natural-language references against the last catalog result.
 * It returns only a SKU-backed query; it never creates catalog facts.
 */
public final class ConversationWorkingMemoryReferenceResolver {

    private static final Pattern EXPLICIT_REFERENCE = Pattern.compile(
            "\\b(este|esta|estos|estas|ese|esa|esos|esas|esto|eso|uno|una|aquel|aquella|"
                    + "agregala|agregalo|sacala|sacalo|quitamela|quitamelo|llevala|llevalo)\\b");
    private static final Pattern ORDINAL_REFERENCE = Pattern.compile(
            "\\b(?:el|la)?\\s*(primero|primera|segundo|segunda|tercero|tercera|arriba)\\b");

    public Optional<CatalogQuery> resolve(ConversationContext context) {
        if (context == null || context.latestMessage() == null) {
            return Optional.empty();
        }
        ConversationWorkingMemory memory = context.selection() == null
                ? ConversationWorkingMemory.empty()
                : context.selection().workingMemory();
        if (!memory.hasCandidates()) {
            return Optional.empty();
        }

        String normalized = normalize(context.latestMessage());
        CatalogQuery current = CatalogQueryParser.parse(context.latestMessage()).orElse(CatalogQuery.empty());
        Optional<CatalogCandidateReference> reference = candidateFor(normalized, memory);
        if (reference.isPresent()) {
            reference = reference.filter(candidate -> matches(candidate, current));
        } else if (!current.isEmpty()) {
            reference = uniqueCandidateMatching(current, memory);
        }
        return reference.map(candidate -> new CatalogQuery(
                null,
                candidate.sku(),
                null,
                null));
    }

    public Optional<CatalogQuery> resolveForCartMutation(ConversationContext context) {
        if (context == null || context.latestMessage() == null || context.selection() == null) {
            return Optional.empty();
        }
        ConversationWorkingMemory memory = context.selection().workingMemory();
        if (!memory.hasCandidates()) {
            return Optional.empty();
        }
        String normalized = normalize(context.latestMessage());
        Optional<CatalogCandidateReference> candidate = candidateFor(normalized, memory);
        CatalogQuery parsedQuery = CartCommandParser.parse(context.latestMessage()).query();
        final CatalogQuery explicitQuery = parsedQuery == null ? CatalogQuery.empty() : parsedQuery;
        if (candidate.isPresent()) {
            candidate = candidate.filter(value -> matches(value, explicitQuery));
        } else if (!explicitQuery.isEmpty()) {
            candidate = uniqueCandidateMatching(explicitQuery, memory);
        }
        return candidate.map(value -> new CatalogQuery(null, value.sku(), null, null));
    }

    public Optional<String> resolveSku(ConversationContext context) {
        return resolve(context).map(CatalogQuery::sku);
    }

    private static Optional<CatalogCandidateReference> candidateFor(
            String normalized,
            ConversationWorkingMemory memory) {
        Matcher ordinal = ORDINAL_REFERENCE.matcher(normalized);
        if (ordinal.find()) {
            int index = switch (ordinal.group(1)) {
                case "primero", "primera", "arriba" -> 0;
                case "segundo", "segunda" -> 1;
                case "tercero", "tercera" -> 2;
                default -> -1;
            };
            return memory.candidateAt(index);
        }
        if (!containsReference(normalized)) {
            return Optional.empty();
        }
        return memory.focusedCandidate();
    }

    private static Optional<CatalogCandidateReference> uniqueCandidateMatching(
            CatalogQuery query,
            ConversationWorkingMemory memory) {
        List<CatalogCandidateReference> matches = memory.catalogCandidates().stream()
                .filter(candidate -> matches(candidate, query))
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private static boolean matches(CatalogCandidateReference candidate, CatalogQuery query) {
        if (query == null || query.minPrice() != null || query.maxPrice() != null) {
            return false;
        }
        if (query.isEmpty()) {
            return true;
        }
        if (query.sku() != null && !query.sku().equalsIgnoreCase(candidate.sku())) {
            return false;
        }
        if (query.name() != null && !normalize(candidate.productName()).contains(normalize(query.name()))) {
            return false;
        }
        if (query.productType() != null && !hasProductType(candidate.productName(), query.productType())) {
            return false;
        }
        if (query.size() != null && !equalsNormalized(query.size(), candidate.size())) {
            return false;
        }
        return query.color() == null || equalsNormalized(query.color(), candidate.color());
    }

    private static boolean hasProductType(String productName, String productType) {
        String name = normalize(productName);
        String type = normalize(productType);
        if (type.equals("camiseta")) {
            return name.contains("remera") || name.contains("camiseta");
        }
        if (type.equals("sudadera")) {
            return name.contains("buzo") || name.contains("sudadera");
        }
        return name.contains(type);
    }

    private static boolean equalsNormalized(String expected, String actual) {
        return actual != null && normalize(expected).equals(normalize(actual));
    }

    private static boolean containsReference(String normalized) {
        return EXPLICIT_REFERENCE.matcher(normalized).find();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
