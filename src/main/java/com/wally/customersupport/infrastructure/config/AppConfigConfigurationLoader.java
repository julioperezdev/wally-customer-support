package com.wally.customersupport.infrastructure.config;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import software.amazon.awssdk.services.appconfigdata.AppConfigDataClient;
import software.amazon.awssdk.services.appconfigdata.model.GetLatestConfigurationRequest;
import software.amazon.awssdk.services.appconfigdata.model.StartConfigurationSessionRequest;

/** Loads one immutable AppConfig snapshot for the application startup. */
final class AppConfigConfigurationLoader {

    private final AppConfigDataClient client;
    private final ObjectMapper objectMapper;

    AppConfigConfigurationLoader(AppConfigDataClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    Map<String, Object> load(ExternalConfigurationProperties.AppConfig properties) {
        var session = client.startConfigurationSession(StartConfigurationSessionRequest.builder()
                .applicationIdentifier(required(properties.application(), "application"))
                .environmentIdentifier(required(properties.environment(), "environment"))
                .configurationProfileIdentifier(required(properties.profile(), "profile"))
                .build());

        var response = client.getLatestConfiguration(GetLatestConfigurationRequest.builder()
                .configurationToken(session.initialConfigurationToken())
                .build());

        if (response.configuration() == null || response.configuration().asByteArray().length == 0) {
            return Map.of();
        }

        try {
            JsonNode root = objectMapper.readTree(response.configuration().asByteArray());
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("AWS AppConfig payload must be a JSON object");
            }

            Map<String, Object> flattened = new LinkedHashMap<>();
            flatten(root, "", flattened);
            return flattened;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse AWS AppConfig JSON configuration", exception);
        }
    }

    private static void flatten(JsonNode node, String prefix, Map<String, Object> target) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                String childKey = prefix.isBlank() ? entry.getKey() : prefix + "." + entry.getKey();
                flatten(entry.getValue(), childKey, target);
            });
        } else if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                flatten(node.get(index), prefix + "[" + index + "]", target);
            }
        } else if (!node.isNull()) {
            target.put(prefix, node.isTextual() ? node.textValue() : node.asText());
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AWS AppConfig " + name + " must not be blank when enabled");
        }
        return value;
    }
}
