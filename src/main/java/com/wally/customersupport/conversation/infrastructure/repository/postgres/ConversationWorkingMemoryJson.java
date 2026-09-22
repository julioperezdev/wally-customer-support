package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.List;

import com.wally.customersupport.conversation.domain.model.CatalogCandidateReference;
import com.wally.customersupport.conversation.domain.model.ConversationWorkingMemory;

/** Persistence-only JSON shape for the bounded commercial working memory. */
public record ConversationWorkingMemoryJson(
        List<CatalogCandidateReferenceJson> catalogCandidates,
        String focusedSku,
        String pendingAction,
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
                normalized.pendingAction(),
                normalized.lastCatalogStatus(),
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
                pendingAction,
                lastCatalogStatus,
                timestamp);
    }

    record CatalogCandidateReferenceJson(
            String productName,
            String sku,
            String size,
            String color,
            String imageReference) {

        static CatalogCandidateReferenceJson fromDomain(CatalogCandidateReference candidate) {
            return new CatalogCandidateReferenceJson(
                    candidate.productName(),
                    candidate.sku(),
                    candidate.size(),
                    candidate.color(),
                    candidate.imageReference());
        }

        CatalogCandidateReference toDomain() {
            try {
                return new CatalogCandidateReference(productName, sku, size, color, imageReference);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }
}
