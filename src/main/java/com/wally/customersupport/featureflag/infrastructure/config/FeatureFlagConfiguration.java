package com.wally.customersupport.featureflag.infrastructure.config;

import com.wally.customersupport.featureflag.application.port.FeatureFlagConfigurationPublisher;
import com.wally.customersupport.featureflag.application.port.FeatureFlagConfigurationSource;
import com.wally.customersupport.featureflag.infrastructure.appconfig.AppConfigFeatureFlagConfigurationPublisher;
import com.wally.customersupport.featureflag.infrastructure.appconfig.AppConfigFeatureFlagConfigurationSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.appconfig.AppConfigClient;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;

@Configuration(proxyBeanMethods = false)
public class FeatureFlagConfiguration {

    @Bean
    @ConditionalOnMissingBean
    AppConfigDataClient featureFlagAppConfigDataClient() {
        return AppConfigDataClient.builder().build();
    }

    @Bean
    @ConditionalOnMissingBean
    AppConfigClient featureFlagAppConfigClient() {
        return AppConfigClient.builder().build();
    }

    @Bean
    FeatureFlagConfigurationSource featureFlagConfigurationSource(
            AppConfigDataClient client,
            tools.jackson.databind.ObjectMapper objectMapper,
            FeatureFlagProperties properties) {
        return new AppConfigFeatureFlagConfigurationSource(client, properties);
    }

    @Bean
    FeatureFlagConfigurationPublisher featureFlagConfigurationPublisher(
            AppConfigClient client,
            FeatureFlagProperties properties) {
        return new AppConfigFeatureFlagConfigurationPublisher(client, properties);
    }
}
