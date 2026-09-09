package com.wally.customersupport.featureflag.application.port;

public interface FeatureFlagConfigurationPublisher {

    Publication publish(String content, String description);

    record Publication(String version, String deploymentNumber) {
    }
}
