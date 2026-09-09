package com.wally.customersupport.featureflag.application;

import java.time.Instant;
import java.util.List;

/** Backoffice-safe projection of the effective configuration. */
public record FeatureFlagSnapshotView(
        String environment,
        String effectiveVersion,
        Instant loadedAt,
        Instant lastSuccessfulRefreshAt,
        boolean stale,
        List<FeatureFlagDefinition> flags,
        List<FeatureFlagAuditEntry> audit) {
}
