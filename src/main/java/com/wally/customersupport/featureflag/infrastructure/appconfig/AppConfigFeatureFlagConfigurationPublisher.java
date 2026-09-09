package com.wally.customersupport.featureflag.infrastructure.appconfig;

import com.wally.customersupport.featureflag.application.port.FeatureFlagConfigurationPublisher;
import com.wally.customersupport.featureflag.infrastructure.config.FeatureFlagProperties;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.appconfig.AppConfigClient;
import software.amazon.awssdk.services.appconfig.model.CreateHostedConfigurationVersionRequest;
import software.amazon.awssdk.services.appconfig.model.StartDeploymentRequest;

/** Optional AppConfig control-plane adapter. It is disabled until IAM and auth are explicitly enabled. */
public class AppConfigFeatureFlagConfigurationPublisher implements FeatureFlagConfigurationPublisher {

    private final AppConfigClient client;
    private final FeatureFlagProperties properties;

    public AppConfigFeatureFlagConfigurationPublisher(AppConfigClient client, FeatureFlagProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public Publication publish(String content, String description) {
        if (!properties.publisher().enabled()) {
            throw new IllegalStateException("feature flag publisher is disabled");
        }
        var appConfig = properties.appconfig();
        var hostedVersion = client.createHostedConfigurationVersion(CreateHostedConfigurationVersionRequest.builder()
                .applicationId(required(appConfig.application(), "application"))
                .configurationProfileId(required(properties.appconfig().profile(), "profile"))
                .content(SdkBytes.fromUtf8String(content))
                .contentType("application/json")
                .description(description)
                .build());
        var deployment = client.startDeployment(StartDeploymentRequest.builder()
                .applicationId(required(appConfig.application(), "application"))
                .environmentId(required(appConfig.environment(), "environment"))
                .configurationProfileId(required(appConfig.profile(), "profile"))
                .configurationVersion(String.valueOf(hostedVersion.versionNumber()))
                .deploymentStrategyId(required(properties.publisher().deploymentStrategyId(), "deploymentStrategyId"))
                .description(description)
                .build());
        return new Publication(String.valueOf(hostedVersion.versionNumber()), String.valueOf(deployment.deploymentNumber()));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
