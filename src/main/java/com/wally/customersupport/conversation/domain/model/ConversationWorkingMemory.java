package com.wally.customersupport.conversation.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Short-lived, structured working memory for the active commercial flow.
 *
 * <p>It keeps only bounded references to the last catalog result so natural
 * references such as "esa", "el segundo" or "agregala" can be resolved
 * deterministically. It is not a catalog cache and never replaces a fresh
 * authoritative lookup.</p>
 */
public record ConversationWorkingMemory(
        List<CatalogCandidateReference> catalogCandidates,
        String focusedSku,
        String pendingAction,
        String lastCatalogStatus,
        Instant updatedAt) {

    private static final int MAX_CANDIDATES = 10;

    public ConversationWorkingMemory {
        catalogCandidates = catalogCandidates == null
                ? List.of()
                : catalogCandidates.stream()
                        .filter(Objects::nonNull)
                        .distinct()
                        .limit(MAX_CANDIDATES)
                        .toList();
        focusedSku = normalize(focusedSku);
        pendingAction = normalize(pendingAction);
        lastCatalogStatus = normalize(lastCatalogStatus);
    }

    public static ConversationWorkingMemory empty() {
        return new ConversationWorkingMemory(List.of(), null, null, null, null);
    }

    public static ConversationWorkingMemory cleared(Instant timestamp) {
        return new ConversationWorkingMemory(List.of(), null, null, "CLEARED", timestamp);
    }

    public boolean hasCatalogObservation() {
        return lastCatalogStatus != null;
    }

    public boolean hasCandidates() {
        return !catalogCandidates.isEmpty();
    }

    public Optional<CatalogCandidateReference> focusedCandidate() {
        if (focusedSku == null) {
            return Optional.empty();
        }
        return catalogCandidates.stream()
                .filter(candidate -> candidate.sku().equalsIgnoreCase(focusedSku))
                .findFirst();
    }

    public Optional<CatalogCandidateReference> candidateAt(int zeroBasedIndex) {
        if (zeroBasedIndex < 0 || zeroBasedIndex >= catalogCandidates.size()) {
            return Optional.empty();
        }
        return Optional.of(catalogCandidates.get(zeroBasedIndex));
    }

    public ConversationWorkingMemory withPendingAction(String action) {
        return new ConversationWorkingMemory(
                catalogCandidates,
                focusedSku,
                action,
                lastCatalogStatus,
                updatedAt);
    }

    public static ConversationWorkingMemory catalogObservation(
            List<CatalogCandidateReference> candidates,
            String status,
            Instant timestamp) {
        List<CatalogCandidateReference> bounded = candidates == null ? List.of() : candidates;
        String focused = bounded.size() == 1 ? bounded.getFirst().sku() : null;
        return new ConversationWorkingMemory(
                bounded,
                focused,
                "CATALOG_SEARCH",
                status,
                timestamp);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
