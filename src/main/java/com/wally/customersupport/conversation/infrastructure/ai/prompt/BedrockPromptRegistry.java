package com.wally.customersupport.conversation.infrastructure.ai.prompt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.wally.customersupport.shared.infrastructure.config.PromptManagementProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockagent.BedrockAgentClient;
import software.amazon.awssdk.services.bedrockagent.model.GetPromptRequest;
import software.amazon.awssdk.services.bedrockagent.model.GetPromptResponse;
import software.amazon.awssdk.services.bedrockagent.model.PromptTemplateConfiguration;
import software.amazon.awssdk.services.bedrockagent.model.PromptVariant;

/** Reads immutable text prompts from Amazon Bedrock Prompt Management. */
@Component
@ConditionalOnProperty(name = "wcs.ai.prompt.provider", havingValue = "bedrock")
public class BedrockPromptRegistry implements PromptRegistry {

    private final BedrockAgentClient client;
    private final PromptManagementProperties properties;

    public BedrockPromptRegistry(BedrockAgentClient client, PromptManagementProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public PromptDefinition intentPrompt(String ignoredVersion) {
        return load(
                "conversation-intent",
                properties.effectiveIntentIdentifier(),
                properties.effectiveIntentVersion());
    }

    @Override
    public PromptDefinition responsePrompt(String ignoredVersion) {
        return load(
                "conversation-response",
                properties.effectiveResponseIdentifier(),
                properties.effectiveResponseVersion());
    }

    private PromptDefinition load(String logicalId, String identifier, String version) {
        GetPromptResponse response = client.getPrompt(GetPromptRequest.builder()
                .promptIdentifier(identifier)
                .promptVersion(version)
                .build());
        PromptVariant variant = selectVariant(response);
        String content = extractText(variant);
        String resolvedVersion = response.version() == null || response.version().isBlank()
                ? version
                : response.version();
        return new PromptDefinition(logicalId, resolvedVersion, content, sha256(content));
    }

    private PromptVariant selectVariant(GetPromptResponse response) {
        if (response == null || response.variants() == null || response.variants().isEmpty()) {
            throw new IllegalStateException("Bedrock prompt has no variants");
        }
        String defaultVariant = response.defaultVariant();
        if (defaultVariant != null && !defaultVariant.isBlank()) {
            return response.variants().stream()
                    .filter(variant -> defaultVariant.equals(variant.name()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Bedrock prompt default variant was not found"));
        }
        return response.variants().getFirst();
    }

    private String extractText(PromptVariant variant) {
        PromptTemplateConfiguration template = variant == null ? null : variant.templateConfiguration();
        String text = template == null || template.text() == null ? null : template.text().text();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("Bedrock prompt variant does not contain a text template");
        }
        return text.trim();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                result.append(String.format("%02x", current));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
