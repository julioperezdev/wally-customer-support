package com.wally.customersupport.agent.application.service;

import java.util.Objects;

/** Outcome of one bounded, deterministic catalog specialist execution. */
public record CatalogSpecialistExecutionResult(
        Status status,
        String reason,
        String response,
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
        if (status == Status.EXECUTED && (response == null || response.isBlank())) {
            throw new IllegalArgumentException("executed result requires a response");
        }
        if (status == Status.FALLBACK && response != null) {
            throw new IllegalArgumentException("fallback result must not expose a response");
        }
    }

    public boolean executed() {
        return status == Status.EXECUTED;
    }
}
