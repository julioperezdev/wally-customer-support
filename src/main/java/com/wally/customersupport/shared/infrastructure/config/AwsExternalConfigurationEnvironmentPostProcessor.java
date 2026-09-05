package com.wally.customersupport.shared.infrastructure.config;

import java.util.Map;

import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * Adds AWS-backed configuration before Spring binds application properties.
 * Bootstrap identifiers are versioned with the application; payload values are
 * never logged. AWS credentials are resolved by the SDK default credentials
 * provider chain, so the same code works with a local AWS profile and an AWS
 * workload role.
 */
public final class AwsExternalConfigurationEnvironmentPostProcessor
        implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AwsExternalConfigurationEnvironmentPostProcessor.class);
    private static final String CONFIG_PREFIX = "wcs.external-config";
    private static final String APPCONFIG_SOURCE = "awsAppConfig";
    private static final String SECRETS_SOURCE = "awsSecretsManager";
    private static final Region AWS_REGION = Region.US_EAST_1;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        ExternalConfigurationProperties bootstrap = bind(environment);
        if (bootstrap == null) {
            return;
        }

        if (bootstrap.appconfig() != null && bootstrap.appconfig().enabled()) {
            loadAppConfig(environment, bootstrap.appconfig());
        }

        ExternalConfigurationProperties effective = bind(environment);
        if (effective != null && effective.secretsManager() != null && effective.secretsManager().enabled()) {
            loadSecrets(environment, effective.secretsManager());
        }
    }

    private void loadAppConfig(ConfigurableEnvironment environment,
            ExternalConfigurationProperties.AppConfig properties) {
        try (AppConfigDataClient client = buildAppConfigClient()) {
            Map<String, Object> values = new AppConfigConfigurationLoader(client, new ObjectMapper()).load(properties);
            if (values.isEmpty()) {
                handleFailure(properties.failFast(), "AWS AppConfig returned an empty configuration", null);
                return;
            }
            addAfterSystemEnvironment(environment, APPCONFIG_SOURCE, values);
            log.info("Loaded {} non-secret properties from AWS AppConfig", values.size());
        } catch (RuntimeException exception) {
            handleFailure(properties.failFast(), "Unable to load AWS AppConfig", exception);
        }
    }

    private void loadSecrets(ConfigurableEnvironment environment,
            ExternalConfigurationProperties.SecretsManager properties) {
        try (SecretsManagerClient client = buildSecretsManagerClient()) {
            Map<String, Object> values = new SecretsManagerConfigurationLoader(client, new ObjectMapper()).load(properties);
            if (values.isEmpty()) {
                handleFailure(properties.failFast(), "AWS Secrets Manager returned no configured application values", null);
                return;
            }
            addAfterSystemEnvironment(environment, SECRETS_SOURCE, values);
            log.info("Resolved {} allow-listed application properties from AWS Secrets Manager", values.size());
        } catch (RuntimeException exception) {
            handleFailure(properties.failFast(), "Unable to load AWS Secrets Manager configuration", exception);
        }
    }

    private static ExternalConfigurationProperties bind(ConfigurableEnvironment environment) {
        return Binder.get(environment)
                .bind(CONFIG_PREFIX, ExternalConfigurationProperties.class)
                .orElse(null);
    }

    private static void addAfterSystemEnvironment(ConfigurableEnvironment environment, String name,
            Map<String, Object> values) {
        if (environment.getPropertySources().contains(name)) {
            environment.getPropertySources().remove(name);
        }
        environment.getPropertySources().addAfter(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new MapPropertySource(name, values));
    }

    private static AppConfigDataClient buildAppConfigClient() {
        return AppConfigDataClient.builder()
                .build();
    }

    private static SecretsManagerClient buildSecretsManagerClient() {
        return SecretsManagerClient.builder()
                .build();
    }

    private static void handleFailure(boolean failFast, String message, RuntimeException exception) {
        if (failFast) {
            if (exception == null) {
                throw new IllegalStateException(message);
            }
            throw new IllegalStateException(message, exception);
        }
        if (exception == null) {
            log.warn("{}; continuing with packaged/default configuration", message);
        } else {
            log.warn("{}; continuing with packaged/default configuration: {}", message, exception.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
}
