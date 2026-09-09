package com.wally.customersupport.featureflag.infrastructure.appconfig;

import java.util.Optional;

import com.wally.customersupport.featureflag.application.port.FeatureFlagConfigurationSource;
import com.wally.customersupport.featureflag.infrastructure.config.FeatureFlagProperties;
import org.springframework.util.Assert;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationRequest;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionRequest;

/** AppConfig Data API polling adapter; the SDK token is kept private to the adapter. */
public class AppConfigFeatureFlagConfigurationSource implements FeatureFlagConfigurationSource {

    private final AppConfigDataClient client;
    private final FeatureFlagProperties properties;
    private String pollToken;

    public AppConfigFeatureFlagConfigurationSource(AppConfigDataClient client, FeatureFlagProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public synchronized Optional<FeatureFlagPayload> poll() {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        if (pollToken == null) {
            var appConfig = properties.appconfig();
            Assert.notNull(appConfig, "wcs.feature-flags.appconfig must be configured");
            var session = client.startConfigurationSession(StartConfigurationSessionRequest.builder()
                    .applicationIdentifier(required(appConfig.application(), "application"))
                    .environmentIdentifier(required(appConfig.environment(), "environment"))
                    .configurationProfileIdentifier(required(appConfig.profile(), "profile"))
                    .build());
            pollToken = session.initialConfigurationToken();
        }
        var response = client.getLatestConfiguration(GetLatestConfigurationRequest.builder()
                .configurationToken(required(pollToken, "pollToken"))
                .build());
        pollToken = response.nextPollConfigurationToken();
        byte[] content = response.configuration() == null ? new byte[0] : response.configuration().asByteArray();
        return content.length == 0 ? Optional.empty() : Optional.of(new FeatureFlagPayload(content));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
