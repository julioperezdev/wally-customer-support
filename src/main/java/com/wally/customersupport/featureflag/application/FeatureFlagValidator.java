package com.wally.customersupport.featureflag.application;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Validates the business-flag contract and rejects secrets before publication. */
@Component
public class FeatureFlagValidator {

    private static final String SUPPORTED_SCHEMA_VERSION = "1";
    private static final Pattern KEY = Pattern.compile("wcs\\.(agent|catalog|conversation|human-handoff|memory|rag)\\.[a-z0-9._-]+");
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "secret", "password", "token", "accesskey", "access-key", "private-key", "credential", "authorization");
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "version", "flags");

    private final ObjectMapper objectMapper;

    public FeatureFlagValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FeatureFlagDocument validate(FeatureFlagDocument document, String environment) {
        Objects.requireNonNull(document, "document");
        if (!SUPPORTED_SCHEMA_VERSION.equals(document.schemaVersion())) {
            throw new FeatureFlagValidationException("schemaVersion must be " + SUPPORTED_SCHEMA_VERSION);
        }
        requireText(document.version(), "version");
        requireText(environment, "environment");
        if (document.flags().size() > 200) {
            throw new FeatureFlagValidationException("at most 200 feature flags are supported");
        }
        Set<String> keys = new java.util.HashSet<>();
        for (FeatureFlagDefinition flag : document.flags()) {
            requireText(flag.key(), "flag.key");
            if (!KEY.matcher(flag.key()).matches()) {
                throw new FeatureFlagValidationException("flag key is outside the business allowlist: " + flag.key());
            }
            if (!keys.add(flag.key())) {
                throw new FeatureFlagValidationException("duplicate flag key: " + flag.key());
            }
            validateDimensionValues(flag);
        }
        return document;
    }

    public FeatureFlagDocument validateJson(byte[] payload, String environment) {
        Objects.requireNonNull(payload, "payload");
        try {
            JsonNode root = objectMapper.readTree(payload);
            rejectUnknownRootFields(root);
            rejectSensitiveFields(root);
            return validate(objectMapper.readValue(payload, FeatureFlagDocument.class), environment);
        } catch (FeatureFlagValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new FeatureFlagValidationException("invalid feature flag JSON: " + exception.getMessage());
        }
    }

    private static void validateDimensionValues(FeatureFlagDefinition flag) {
        for (String value : java.util.stream.Stream.of(
                flag.environments(), flag.channels(), flag.useCases(), flag.agentIds())
                .flatMap(java.util.Collection::stream)
                .toList()) {
            if (value == null || value.isBlank() || value.length() > 128) {
                throw new FeatureFlagValidationException("flag dimensions must be non-blank and at most 128 characters");
            }
        }
        if (flag.agentVersions().stream().anyMatch(version -> version == null || version < 1)) {
            throw new FeatureFlagValidationException("agentVersions must contain positive values");
        }
    }

    private static void rejectSensitiveFields(JsonNode node) {
        if (node == null) {
            throw new FeatureFlagValidationException("feature flag payload must not be null");
        }
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                String normalized = entry.getKey().replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
                if (SENSITIVE_NAMES.stream().anyMatch(normalized::contains)) {
                    throw new FeatureFlagValidationException("secret-like fields are not allowed in feature flags");
                }
                rejectSensitiveFields(entry.getValue());
            });
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                rejectSensitiveFields(child);
            }
        }
    }

    private static void rejectUnknownRootFields(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new FeatureFlagValidationException("feature flag payload must be a JSON object");
        }
        root.properties().forEach(entry -> {
            if (!ROOT_FIELDS.contains(entry.getKey())) {
                throw new FeatureFlagValidationException("unknown root field: " + entry.getKey());
            }
        });
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new FeatureFlagValidationException(field + " must be non-blank and at most 128 characters");
        }
    }
}
