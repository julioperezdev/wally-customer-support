package com.wally.customersupport.catalog.application.service;

import java.util.List;
import java.util.Objects;

/** Structured, source-backed result of one catalog use-case execution. */
public record CatalogSearchResult(
        Status status,
        List<CatalogFact> facts,
        String requestedProductType,
        FollowUpKind followUpKind,
        String reason) {

    public enum Status {
        MATCHED,
        NO_MATCH,
        CLARIFICATION,
        AMBIGUOUS,
        ALTERNATIVES
    }

    public enum FollowUpKind {
        NONE,
        AVAILABILITY,
        PRICE,
        SIZE,
        COLOR
    }

    public CatalogSearchResult {
        status = Objects.requireNonNull(status, "status");
        facts = facts == null ? List.of() : List.copyOf(facts);
        requestedProductType = normalize(requestedProductType);
        followUpKind = followUpKind == null ? FollowUpKind.NONE : followUpKind;
        reason = normalize(reason);
        if (status == Status.MATCHED && facts.isEmpty()) {
            throw new IllegalArgumentException("matched result requires at least one fact");
        }
        if ((status == Status.NO_MATCH || status == Status.CLARIFICATION || status == Status.AMBIGUOUS)
                && !facts.isEmpty()) {
            throw new IllegalArgumentException(status + " result must not expose facts");
        }
        if (status != Status.MATCHED && followUpKind != FollowUpKind.NONE) {
            throw new IllegalArgumentException("followUpKind requires a matched result");
        }
    }

    public int resultCount() {
        return facts.size();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
