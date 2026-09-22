package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wally.customersupport.conversation.domain.model.CatalogCandidateReference;
import com.wally.customersupport.conversation.domain.model.CatalogObservationStatus;
import com.wally.customersupport.conversation.domain.model.ConversationWorkingMemory;

/** Persistence-only JSON shape for the bounded commercial working memory. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConversationWorkingMemoryJson(
        List<CatalogCandidateReferenceJson> catalogCandidates,
        String focusedSku,
        String lastCatalogStatus,
        String updatedAt) {

    static ConversationWorkingMemoryJson fromDomain(ConversationWorkingMemory memory) {
        ConversationWorkingMemory normalized = memory == null
                ? ConversationWorkingMemory.empty()
                : memory;
        return new ConversationWorkingMemoryJson(
                normalized.catalogCandidates().stream()
                        .map(CatalogCandidateReferenceJson::fromDomain)
                        .toList(),
                normalized.focusedSku(),
                normalized.lastCatalogStatus() == null ? null : normalized.lastCatalogStatus().name(),
                normalized.updatedAt() == null ? null : normalized.updatedAt().toString());
    }

    ConversationWorkingMemory toDomain() {
        Instant timestamp = null;
        if (updatedAt != null && !updatedAt.isBlank()) {
            try {
                timestamp = Instant.parse(updatedAt);
            } catch (RuntimeException ignored) {
                // Old or malformed context must not prevent the conversation
                // from loading; the authoritative catalog will be queried again.
            }
        }
        return new ConversationWorkingMemory(
                catalogCandidates == null
                        ? List.of()
                        : catalogCandidates.stream().map(CatalogCandidateReferenceJson::toDomain).toList(),
                focusedSku,
                parseStatus(lastCatalogStatus),
                timestamp);
    }

    private static CatalogObservationStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CatalogObservationStatus.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CatalogCandidateReferenceJson(
            String productName,
            String sku,
            String size,
            String color) {

        static CatalogCandidateReferenceJson fromDomain(CatalogCandidateReference candidate) {
            return new CatalogCandidateReferenceJson(
                    candidate.productName(),
                    candidate.sku(),
                    candidate.size(),
                    candidate.color());
        }

        CatalogCandidateReference toDomain() {
            try {
                return new CatalogCandidateReference(productName, sku, size, color);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }
}
