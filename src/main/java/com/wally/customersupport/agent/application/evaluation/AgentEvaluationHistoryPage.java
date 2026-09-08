package com.wally.customersupport.agent.application.evaluation;

import java.util.List;
import java.util.Objects;

/** Bounded page of sanitized evaluation summaries. */
public record AgentEvaluationHistoryPage(
        List<AgentEvaluationRunSummary> items,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages) {

    public AgentEvaluationHistoryPage {
        items = items == null ? List.of() : items.stream()
                .map(item -> Objects.requireNonNull(item, "items must not contain null"))
                .toList();
        if (pageNumber < 0 || pageSize < 1 || pageSize > AgentEvaluationHistoryPageRequest.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("invalid page metadata");
        }
        if (totalElements < 0 || totalPages < 0) {
            throw new IllegalArgumentException("total counts must not be negative");
        }
        if (items.size() > pageSize) {
            throw new IllegalArgumentException("items must not exceed pageSize");
        }
    }

    public boolean hasNext() {
        return pageNumber + 1 < totalPages;
    }

    public boolean isFirst() {
        return pageNumber == 0;
    }

    public boolean isLast() {
        return !hasNext();
    }
}
