package com.wally.customersupport.featureflag.infrastructure.noop;

import java.util.Optional;

import com.wally.customersupport.featureflag.application.port.FeatureFlagConfigurationSource;

/** Source used when runtime feature flags are disabled for the current profile. */
public final class DisabledFeatureFlagConfigurationSource implements FeatureFlagConfigurationSource {

    @Override
    public Optional<FeatureFlagPayload> poll() {
        return Optional.empty();
    }
}
