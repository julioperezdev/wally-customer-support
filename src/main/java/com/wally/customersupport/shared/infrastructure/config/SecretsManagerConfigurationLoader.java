package com.wally.customersupport.shared.infrastructure.config;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

/** Resolves allow-listed application properties from JSON secrets. */
final class SecretsManagerConfigurationLoader {

    private final SecretsManagerClient client;
    private final ObjectMapper objectMapper;

    SecretsManagerConfigurationLoader(SecretsManagerClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    Map<String, Object> load(ExternalConfigurationProperties.SecretsManager properties) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        loadSecret(properties.databaseSecretId(), "database", resolved);
        loadSecret(properties.whatsappSecretId(), "whatsapp", resolved);
        loadSecret(properties.telegramSecretId(), "telegram", resolved);
        String runtimeSecretId = properties.runtimeSecretId();
        if (runtimeSecretId == null || runtimeSecretId.isBlank()) {
            boolean hasDedicatedReferences = isPresent(properties.databaseSecretId())
                    || isPresent(properties.whatsappSecretId())
                    || isPresent(properties.telegramSecretId());
            runtimeSecretId = hasDedicatedReferences ? null : properties.secretId();
        }
        loadSecret(runtimeSecretId, "runtime", resolved);
        return resolved;
    }

    private void loadSecret(String secretId, String kind, Map<String, Object> target) {
        if (secretId == null || secretId.isBlank()) {
            return;
        }

        var response = client.getSecretValue(GetSecretValueRequest.builder().secretId(secretId).build());
        String secret = response.secretString();
        if (secret == null && response.secretBinary() != null) {
            secret = new String(response.secretBinary().asByteArray(), StandardCharsets.UTF_8);
        }
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("AWS Secrets Manager secret has no JSON value for kind " + kind);
        }

        try {
            JsonNode root = objectMapper.readTree(secret);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("secret JSON must be an object");
            }
            switch (kind) {
                case "database" -> mapDatabase(root, target);
                case "whatsapp" -> mapWhatsApp(root, target);
                case "telegram" -> mapTelegram(root, target);
                case "runtime" -> mapRuntime(root, target);
                default -> throw new IllegalArgumentException("Unsupported secret kind: " + kind);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse AWS Secrets Manager JSON for kind " + kind, exception);
        }
    }

    private static void mapDatabase(JsonNode root, Map<String, Object> target) {
        putIfPresent(root, target, "spring.datasource.url", "jdbc-url", "jdbc_url", "url", "database-url",
                "database_url", "SPRING_DATASOURCE_URL");
        putIfPresent(root, target, "spring.datasource.username", "username", "user", "DB_USER",
                "DATABASE_USERNAME");
        putIfPresent(root, target, "spring.datasource.password", "password", "DB_PASSWORD",
                "DATABASE_PASSWORD");
    }

    private static void mapWhatsApp(JsonNode root, Map<String, Object> target) {
        putIfPresent(root, target, "wcs.whatsapp.access-token", "access-token", "access_token", "accessToken",
                "WHATSAPP_ACCESS_TOKEN");
        putIfPresent(root, target, "wcs.whatsapp.verify-token", "verify-token", "verify_token", "verifyToken",
                "WHATSAPP_VERIFY_TOKEN");
        putIfPresent(root, target, "wcs.whatsapp.app-secret", "app-secret", "app_secret", "appSecret",
                "META_APP_SECRET");
    }

    private static void mapTelegram(JsonNode root, Map<String, Object> target) {
        putIfPresent(root, target, "wcs.telegram.bot-token", "bot-token", "bot_token", "botToken",
                "token", "TELEGRAM_BOT_TOKEN");
        putIfPresent(root, target, "wcs.telegram.webhook-secret-token", "webhook-secret-token",
                "webhook_secret_token", "webhookSecretToken", "secret-token", "TELEGRAM_WEBHOOK_SECRET_TOKEN");
    }

    private static void mapRuntime(JsonNode root, Map<String, Object> target) {
        putIfPresent(root, target, "spring.datasource.url", "spring.datasource.url", "jdbc-url", "jdbc_url",
                "DATABASE_URL");
        putIfPresent(root, target, "spring.datasource.username", "spring.datasource.username", "username",
                "DATABASE_USERNAME");
        putIfPresent(root, target, "spring.datasource.password", "spring.datasource.password", "password",
                "DATABASE_PASSWORD");
        putIfPresent(root, target, "wcs.whatsapp.access-token", "wcs.whatsapp.access-token", "access-token",
                "WHATSAPP_ACCESS_TOKEN");
        putIfPresent(root, target, "wcs.whatsapp.verify-token", "wcs.whatsapp.verify-token", "verify-token",
                "WHATSAPP_VERIFY_TOKEN");
        putIfPresent(root, target, "wcs.whatsapp.app-secret", "wcs.whatsapp.app-secret", "app-secret",
                "META_APP_SECRET");
        mapTelegram(root, target);
    }

    private static void putIfPresent(JsonNode root, Map<String, Object> target, String propertyName,
            String... candidates) {
        for (String candidate : candidates) {
            JsonNode value = root.get(candidate);
            if (value != null && !value.isNull()) {
                target.put(propertyName, value.isTextual() ? value.textValue() : value.asText());
                return;
            }
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
