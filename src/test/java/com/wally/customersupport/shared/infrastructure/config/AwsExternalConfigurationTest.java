package com.wally.customersupport.shared.infrastructure.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import tools.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationResponse;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationRequest;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionResponse;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionRequest;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

class AwsExternalConfigurationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void skipsAwsClientsWhenExternalConfigurationIsDisabled() {
        var environment = new MockEnvironment()
                .withProperty("wcs.external-config.appconfig.enabled", "false")
                .withProperty("wcs.external-config.secrets-manager.enabled", "false");

        new AwsExternalConfigurationEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getPropertySources().contains("awsAppConfig")).isFalse();
        assertThat(environment.getPropertySources().contains("awsSecretsManager")).isFalse();
    }

    @Test
    void usesStableProductionAppConfigEnvironment() throws IOException {
        var applicationProperties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/application.properties")) {
            assertThat(input).isNotNull();
            applicationProperties.load(input);
        }

        assertThat(applicationProperties.getProperty("wcs.external-config.appconfig.application"))
                .isEqualTo("wally-customer-support");
        assertThat(applicationProperties.getProperty("wcs.external-config.appconfig.profile"))
                .isEqualTo("runtime");
        assertThat(applicationProperties.getProperty("wcs.external-config.appconfig.environment"))
                .isEqualTo("prod");
    }

    @Test
    void flattensAppConfigJsonIntoSpringProperties() {
        AppConfigDataClient client = mock(AppConfigDataClient.class);
        when(client.startConfigurationSession(org.mockito.ArgumentMatchers.<StartConfigurationSessionRequest>any()))
                .thenReturn(StartConfigurationSessionResponse.builder().initialConfigurationToken("token").build());
        when(client.getLatestConfiguration(org.mockito.ArgumentMatchers.<GetLatestConfigurationRequest>any()))
                .thenReturn(GetLatestConfigurationResponse.builder()
                        .configuration(SdkBytes.fromUtf8String("""
                                {
                                  "wcs": {
                                    "ai": {"provider": "bedrock", "temperature": 0.2},
                                    "features": {"enabled": true}
                                  }
                                }
                                """))
                        .build());

        var properties = new ExternalConfigurationProperties.AppConfig(
                "wally-customer-support", "prod", "runtime", true, true);

        var loaded = new AppConfigConfigurationLoader(client, objectMapper).load(properties);

        assertThat(loaded).containsEntry("wcs.ai.provider", "bedrock")
                .containsEntry("wcs.ai.temperature", "0.2")
                .containsEntry("wcs.features.enabled", "true");
    }

    @Test
    void resolvesOnlyAllowListedDatabaseAndWhatsAppSecretFields() {
        SecretsManagerClient client = mock(SecretsManagerClient.class);
        when(client.getSecretValue(org.mockito.ArgumentMatchers.<GetSecretValueRequest>any()))
                .thenReturn(GetSecretValueResponse.builder()
                        .secretString("""
                                {"jdbc_url":"jdbc:postgresql://db/wcs","username":"db-user","password":"db-password",
                                 "ignored":"must-not-become-a-property"}
                                """)
                        .build())
                .thenReturn(GetSecretValueResponse.builder()
                        .secretString("""
                                {"access-token":"token-value","verify-token":"verify-value","app-secret":"app-value",
                                 "ignored":"must-not-become-a-property"}
                                """)
                        .build())
                .thenReturn(GetSecretValueResponse.builder()
                        .secretString("""
                                {"bot-token":"bot-token-value","webhook-secret-token":"webhook-token-value",
                                 "ignored":"must-not-become-a-property"}
                                """)
                        .build());

        var properties = new ExternalConfigurationProperties.SecretsManager(
                null, null, "database-secret", "whatsapp-secret", "telegram-secret", true, true);

        var loaded = new SecretsManagerConfigurationLoader(client, objectMapper).load(properties);

        assertThat(loaded).containsEntry("spring.datasource.url", "jdbc:postgresql://db/wcs")
                .containsEntry("spring.datasource.username", "db-user")
                .containsEntry("spring.datasource.password", "db-password")
                .containsEntry("wcs.whatsapp.access-token", "token-value")
                .containsEntry("wcs.whatsapp.verify-token", "verify-value")
                .containsEntry("wcs.whatsapp.app-secret", "app-value")
                .containsEntry("wcs.telegram.bot-token", "bot-token-value")
                .containsEntry("wcs.telegram.webhook-secret-token", "webhook-token-value")
                .doesNotContainKey("ignored");
    }
}
