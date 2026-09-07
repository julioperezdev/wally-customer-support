package com.wally.customersupport.agent.application.service;

import java.util.Objects;

import com.wally.customersupport.catalog.application.service.CatalogSearchResult;

/** Outcome of one bounded, deterministic catalog specialist execution. */
public record CatalogSpecialistExecutionResult(
        Status status,
        String reason,
        CatalogSearchResult result,
        long durationMs) {

    public enum Status {
        EXECUTED,
        FALLBACK
    }

    public CatalogSpecialistExecutionResult {
        status = Objects.requireNonNull(status, "status");
        reason = Objects.requireNonNull(reason, "reason");
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs must not be negative");
        }
        if (status == Status.EXECUTED && result == null) {
            throw new IllegalArgumentException("executed result requires structured facts");
        }
        if (status == Status.FALLBACK && result != null) {
            throw new IllegalArgumentException("fallback result must not expose structured facts");
        }
    }

    public boolean executed() {
        return status == Status.EXECUTED;
    }
}
