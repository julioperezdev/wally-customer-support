package com.wally.customersupport.agent.application.evaluation;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.domain.model.AgentInvocationConfiguration;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import com.wally.customersupport.agent.domain.model.AgentVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Resolves a specific immutable SQL version without consulting runtime activations. */
@Service
@RequiredArgsConstructor
public class AgentEvaluationVersionResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z][a-z0-9_]*)\\}\\}");
    private static final Set<String> ALLOWED_PLACEHOLDERS = Set.of(
            "use_case", "channel", "approved_knowledge", "required_facts",
            "prompt_version", "conversation_history", "latest_customer_message",
            "conversation_summary_section", "customer_preferences_section", "active_selection_section");

    private final AgentRegistryRepository registry;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AgentEvaluationRunRequest resolve(AgentEvaluationRunRequest request) {
        AgentEvaluationRunRequest selected = Objects.requireNonNull(request, "request");
        AgentVersion version = findVersion(selected.agentId(), selected.agentVersion());
        if (version.state() == AgentLifecycleState.DRAFT) {
            throw new IllegalArgumentException("draft agent versions cannot be evaluated");
        }
        if (!"bedrock".equalsIgnoreCase(version.modelProvider())) {
            throw new IllegalArgumentException("selected SQL agent version is not a Bedrock profile");
        }
        validateExecutableSnapshot(version);
        return selected.withVersionDefinition(version);
    }

    private AgentVersion findVersion(String agentId, String requestedVersion) {
        try {
            int versionNumber = Integer.parseInt(requestedVersion);
            return registry.findVersion(agentId, versionNumber)
                    .orElseThrow(() -> new IllegalArgumentException("selected SQL agent version was not found"));
        } catch (NumberFormatException ignored) {
            return registry.findVersions(agentId).stream()
                    .filter(version -> requestedVersion.equals(version.semanticVersion()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("selected SQL agent version was not found"));
        }
    }

    private void validateExecutableSnapshot(AgentVersion version) {
        AgentInvocationConfiguration configuration = version.invocationConfiguration();
        if (configuration.systemPrompt().isBlank() || configuration.userPromptTemplate().isBlank()) {
            throw new IllegalArgumentException("selected SQL agent version has no executable prompt profile");
        }
        if (!AgentInvocationConfiguration.sha256(configuration.systemPrompt().trim())
                .equalsIgnoreCase(version.systemPromptHash())) {
            throw new IllegalArgumentException("selected SQL agent version prompt hash is invalid");
        }
        if (configuration.pricingVersion() == null
                || configuration.inputPriceUsdPerMillionTokens() == null
                || configuration.outputPriceUsdPerMillionTokens() == null) {
            throw new IllegalArgumentException("selected SQL agent version has no pricing profile");
        }
        try {
            if (!objectMapper.readTree(configuration.inputSchemaJson()).isObject()
                    || !objectMapper.readTree(configuration.outputSchemaJson()).isObject()) {
                throw new IllegalArgumentException("agent evaluation schemas must be JSON objects");
            }
            validatePlaceholders(configuration.userPromptTemplate());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("selected SQL agent version has an invalid evaluation template or schema");
        }
    }

    private static void validatePlaceholders(String template) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            if (!ALLOWED_PLACEHOLDERS.contains(matcher.group(1))) {
                throw new IllegalArgumentException("unsupported evaluation prompt placeholder");
            }
        }
        String withoutPlaceholders = matcher.replaceAll("");
        if (withoutPlaceholders.contains("{{") || withoutPlaceholders.contains("}}")) {
            throw new IllegalArgumentException("malformed evaluation prompt placeholder");
        }
    }
}
