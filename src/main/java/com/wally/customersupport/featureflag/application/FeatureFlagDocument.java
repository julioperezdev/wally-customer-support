package com.wally.customersupport.featureflag.application;

import java.util.List;

/** Versioned, non-secret AppConfig document for business feature flags. */
public record FeatureFlagDocument(
        String schemaVersion,
        String version,
        List<FeatureFlagDefinition> flags) {

    public FeatureFlagDocument {
        schemaVersion = schemaVersion == null ? null : schemaVersion.strip();
        version = version == null ? null : version.strip();
        flags = flags == null ? List.of() : List.copyOf(flags);
    }
}
