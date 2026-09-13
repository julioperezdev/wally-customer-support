package com.wally.customersupport.backoffice.infrastructure.config;

import com.wally.customersupport.backoffice.application.port.out.BackofficeIdentityProvider;
import com.wally.customersupport.backoffice.application.service.BackofficeAuthenticationService;
import com.wally.customersupport.backoffice.infrastructure.cognito.CognitoBackofficeIdentityProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BackofficeAuthenticationProperties.class)
public class BackofficeAuthenticationConfiguration {

    @Bean(destroyMethod = "close")
    CognitoIdentityProviderClient cognitoIdentityProviderClient(
            BackofficeAuthenticationProperties properties) {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(properties.cognito().region()))
                .build();
    }

    @Bean
    BackofficeIdentityProvider backofficeIdentityProvider(
            CognitoIdentityProviderClient client,
            BackofficeAuthenticationProperties properties) {
        return new CognitoBackofficeIdentityProvider(client, properties);
    }

    @Bean
    BackofficeAuthenticationService backofficeAuthenticationService(
            BackofficeIdentityProvider identityProvider,
            BackofficeAuthenticationProperties properties) {
        return new BackofficeAuthenticationService(identityProvider, properties);
    }
}
