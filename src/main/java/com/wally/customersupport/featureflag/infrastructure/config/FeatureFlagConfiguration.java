package com.wally.customersupport.featureflag.infrastructure.config;

import com.wally.customersupport.featureflag.application.port.FeatureFlagConfigurationPublisher;
import com.wally.customersupport.featureflag.application.port.FeatureFlagConfigurationSource;
import com.wally.customersupport.featureflag.infrastructure.appconfig.AppConfigFeatureFlagConfigurationPublisher;
import com.wally.customersupport.featureflag.infrastructure.appconfig.AppConfigFeatureFlagConfigurationSource;
import com.wally.customersupport.featureflag.infrastructure.noop.DisabledFeatureFlagConfigurationPublisher;
import com.wally.customersupport.featureflag.infrastructure.noop.DisabledFeatureFlagConfigurationSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.appconfig.AppConfigClient;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;

@Configuration(proxyBeanMethods = false)
public class FeatureFlagConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "wcs.feature-flags", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    AppConfigDataClient featureFlagAppConfigDataClient() {
        return AppConfigDataClient.builder().build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "wcs.feature-flags.publisher", name = "enabled", havingValue = "true")
    @ConditionalOnMissingBean
    AppConfigClient featureFlagAppConfigClient() {
        return AppConfigClient.builder().build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "wcs.feature-flags", name = "enabled", havingValue = "true")
    FeatureFlagConfigurationSource featureFlagConfigurationSource(
            AppConfigDataClient client,
            tools.jackson.databind.ObjectMapper objectMapper,
            FeatureFlagProperties properties) {
        return new AppConfigFeatureFlagConfigurationSource(client, properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "wcs.feature-flags", name = "enabled", havingValue = "false", matchIfMissing = true)
    FeatureFlagConfigurationSource disabledFeatureFlagConfigurationSource() {
        return new DisabledFeatureFlagConfigurationSource();
    }

    @Bean
    @ConditionalOnProperty(prefix = "wcs.feature-flags.publisher", name = "enabled", havingValue = "true")
    FeatureFlagConfigurationPublisher featureFlagConfigurationPublisher(
            AppConfigClient client,
            FeatureFlagProperties properties) {
        return new AppConfigFeatureFlagConfigurationPublisher(client, properties);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "wcs.feature-flags.publisher",
            name = "enabled",
            havingValue = "false",
            matchIfMissing = true)
    FeatureFlagConfigurationPublisher disabledFeatureFlagConfigurationPublisher() {
        return new DisabledFeatureFlagConfigurationPublisher();
    }
}
