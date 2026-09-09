package com.wally.customersupport.featureflag.infrastructure.noop;

import com.wally.customersupport.featureflag.application.port.FeatureFlagConfigurationPublisher;

/** Publisher used when the AppConfig control plane is intentionally disabled. */
public final class DisabledFeatureFlagConfigurationPublisher implements FeatureFlagConfigurationPublisher {

    @Override
    public Publication publish(String content, String description) {
        throw new IllegalStateException("feature flag publisher is disabled");
    }
}
