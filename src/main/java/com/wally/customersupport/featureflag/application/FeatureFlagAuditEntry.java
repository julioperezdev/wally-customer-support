package com.wally.customersupport.featureflag.application;

import java.time.Instant;

/** Sanitized audit record. It never contains the payload or an actor token. */
public record FeatureFlagAuditEntry(
        String operation,
        String actor,
        String version,
        Instant timestamp,
        String result,
        String reason) {
}
