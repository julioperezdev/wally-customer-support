package com.wally.customersupport.backoffice.application.model;

import java.time.Instant;
import java.util.List;

public record BackofficeAgentMap(
        Instant generatedAt,
        BackofficeAgentMapQuery filters,
        List<BackofficeUseCaseMap> useCases,
        long evidenceRunsScanned,
        boolean evidenceTruncated) {

    public BackofficeAgentMap {
        useCases = useCases == null ? List.of() : List.copyOf(useCases);
        if (evidenceRunsScanned < 0) {
            throw new IllegalArgumentException("evidenceRunsScanned must not be negative");
        }
    }
}
