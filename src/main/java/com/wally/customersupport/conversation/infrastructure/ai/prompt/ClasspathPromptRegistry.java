package com.wally.customersupport.conversation.infrastructure.ai.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Loads only prompt versions packaged and reviewed with the application artifact. */
@Component
@ConditionalOnProperty(name = "wcs.ai.prompt.provider", havingValue = "classpath", matchIfMissing = true)
public class ClasspathPromptRegistry implements PromptRegistry {

    @Override
    public PromptDefinition intentPrompt(String version) {
        return load("conversation-intent", version);
    }

    @Override
    public PromptDefinition responsePrompt(String version) {
        return load("conversation-response", version);
    }

    private PromptDefinition load(String promptId, String version) {
        String normalizedVersion = normalizeVersion(version);
        ClassPathResource resource = new ClassPathResource(
                "prompts/" + normalizedVersion + ".system.md");
        if (!resource.exists()) {
            throw new IllegalStateException("Approved prompt version was not found");
        }
        try (InputStream input = resource.getInputStream()) {
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
            return new PromptDefinition(
                    promptId,
                    normalizedVersion,
                    content,
                    sha256(content));
        } catch (IOException exception) {
            throw new IllegalStateException("Approved prompt could not be loaded", exception);
        }
    }

    private static String normalizeVersion(String version) {
        String normalized = version == null ? "" : version.trim();
        if (normalized.isBlank() || !normalized.matches("[a-zA-Z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid prompt version");
        }
        return normalized;
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
