package com.wally.customersupport.conversation.application.tool;

import java.util.Objects;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Immutable metadata that can later be mapped to Bedrock tool definitions. */
public record WcsToolDescriptor(
        String name,
        String description,
        String inputSchemaVersion,
        String inputSchemaJson,
        String outputSchemaVersion,
        String outputSchemaJson,
        String requiredCapability) {

    /** Compatibility constructor for small tools and tests that only define input metadata. */
    public WcsToolDescriptor(
            String name,
            String description,
            String inputSchemaVersion,
            String inputSchemaJson) {
        this(name, description, inputSchemaVersion, inputSchemaJson,
                "output-v1", "{\"type\":\"object\"}", name);
    }

    public WcsToolDescriptor {
        name = required(name, "name");
        description = required(description, "description");
        inputSchemaVersion = required(inputSchemaVersion, "inputSchemaVersion");
        inputSchemaJson = required(inputSchemaJson, "inputSchemaJson");
        outputSchemaVersion = required(outputSchemaVersion, "outputSchemaVersion");
        outputSchemaJson = required(outputSchemaJson, "outputSchemaJson");
        requiredCapability = required(requiredCapability, "requiredCapability");
        validateObjectSchema(inputSchemaJson, "inputSchemaJson");
        validateObjectSchema(outputSchemaJson, "outputSchemaJson");
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static void validateObjectSchema(String schema, String field) {
        try {
            JsonNode root = new ObjectMapper().readTree(schema);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException(field + " must be a JSON object schema");
            }
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException
                    && exception.getMessage() != null
                    && exception.getMessage().contains("must be a JSON object schema")) {
                throw exception;
            }
            throw new IllegalArgumentException(field + " must contain valid JSON", exception);
        }
    }
}
