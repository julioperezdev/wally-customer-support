package com.wally.customersupport.catalog.application.service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Structured, source-backed result of one catalog use-case execution. */
public record CatalogSearchResult(
        Status status,
        List<CatalogFact> facts,
        String requestedProductType,
        FollowUpKind followUpKind,
        String reason,
        List<CatalogImage> images) {

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
        images = images == null ? List.of() : List.copyOf(images);
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

    public CatalogSearchResult(
            Status status,
            List<CatalogFact> facts,
            String requestedProductType,
            FollowUpKind followUpKind,
            String reason) {
        this(status, facts, requestedProductType, followUpKind, reason, List.of());
    }

    public int resultCount() {
        return facts.size();
    }

    /**
     * Returns an image only for one initial, unambiguous catalog match. This
     * avoids sending a gallery or repeating an image on stock/price follow-ups.
     */
    public Optional<String> singleImageReference() {
        if (status != Status.MATCHED || followUpKind != FollowUpKind.NONE || facts.size() != 1) {
            return Optional.empty();
        }
        String sku = facts.getFirst().sku();
        return images.stream()
                .filter(image -> image.sku().equals(sku))
                .map(CatalogImage::reference)
                .findFirst();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
