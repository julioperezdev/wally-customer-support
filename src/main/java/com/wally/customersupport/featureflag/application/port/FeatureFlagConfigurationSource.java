package com.wally.customersupport.featureflag.application.port;

import java.util.Optional;

/** Provider-neutral source of a newly available feature-flag payload. */
public interface FeatureFlagConfigurationSource {

    Optional<FeatureFlagPayload> poll();

    record FeatureFlagPayload(byte[] content) {
        public FeatureFlagPayload {
            content = content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
