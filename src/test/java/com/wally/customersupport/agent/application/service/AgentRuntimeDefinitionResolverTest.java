package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.domain.model.AgentActivation;
import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import com.wally.customersupport.agent.domain.model.AgentVersion;
import org.junit.jupiter.api.Test;

class AgentRuntimeDefinitionResolverTest {

    private static final AgentActivationKey KEY = new AgentActivationKey(
            "catalog-specialist", "prod", "telegram", "catalog-search");
    private static final Instant CREATED_AT = Instant.parse("2026-09-07T18:00:00Z");

    @Test
    void resolvesTheExactPublishedVersionIntoAnImmutableDefinition() {
        AgentActivationResolver activationResolver = mock(AgentActivationResolver.class);
        AgentRegistryRepository registry = mock(AgentRegistryRepository.class);
        when(activationResolver.resolve(KEY)).thenReturn(AgentActivationResolution.active("catalog-specialist", 2));
        when(registry.findVersion("catalog-specialist", 2))
                .thenReturn(Optional.of(version("catalog-specialist", 2, AgentLifecycleState.APPROVED)));

        AgentRuntimeDefinitionResolution resolution = new AgentRuntimeDefinitionResolver(
                activationResolver, registry).resolve(KEY);

        assertThat(resolution.status()).isEqualTo(AgentDefinitionResolutionStatus.ACTIVE);
        assertThat(resolution.reason()).isEqualTo(AgentDefinitionResolutionReason.ACTIVE);
        assertThat(resolution.definition().agentId()).isEqualTo("catalog-specialist");
        assertThat(resolution.definition().agentVersion()).isEqualTo(2);
        assertThat(resolution.definition().modelId()).isEqualTo("openai.gpt-oss-20b-1:0");
        assertThat(resolution.definition().allowedTools()).containsExactly("catalog.search");
        assertThatThrownBy(() -> resolution.definition().allowedTools().add("unsafe.tool"))
                .isInstanceOf(UnsupportedOperationException.class);
        verify(registry).findVersion("catalog-specialist", 2);
    }

    @Test
    void preservesActivationFallbackWithoutReadingTheVersion() {
        AgentActivationResolver activationResolver = mock(AgentActivationResolver.class);
        AgentRegistryRepository registry = mock(AgentRegistryRepository.class);
        when(activationResolver.resolve(KEY))
                .thenReturn(AgentActivationResolution.fallback(AgentResolutionReason.DISABLED));

        AgentRuntimeDefinitionResolution resolution = new AgentRuntimeDefinitionResolver(
                activationResolver, registry).resolve(KEY);

        assertThat(resolution.status()).isEqualTo(AgentDefinitionResolutionStatus.FALLBACK);
        assertThat(resolution.reason()).isEqualTo(AgentDefinitionResolutionReason.DISABLED);
        assertThat(resolution.definition()).isNull();
        verifyNoInteractions(registry);
    }

    @Test
    void fallsBackWhenTheActivatedVersionDoesNotExist() {
        AgentActivationResolver activationResolver = mock(AgentActivationResolver.class);
        AgentRegistryRepository registry = mock(AgentRegistryRepository.class);
        when(activationResolver.resolve(KEY)).thenReturn(AgentActivationResolution.active("catalog-specialist", 2));
        when(registry.findVersion("catalog-specialist", 2)).thenReturn(Optional.empty());

        AgentRuntimeDefinitionResolution resolution = new AgentRuntimeDefinitionResolver(
                activationResolver, registry).resolve(KEY);

        assertThat(resolution.reason()).isEqualTo(AgentDefinitionResolutionReason.VERSION_NOT_FOUND);
        assertThat(resolution.definition()).isNull();
    }

    @Test
    void rejectsAStoredVersionThatDoesNotMatchTheActivationReference() {
        AgentActivationResolver activationResolver = mock(AgentActivationResolver.class);
        AgentRegistryRepository registry = mock(AgentRegistryRepository.class);
        when(activationResolver.resolve(KEY)).thenReturn(AgentActivationResolution.active("catalog-specialist", 2));
        when(registry.findVersion("catalog-specialist", 2))
                .thenReturn(Optional.of(version("catalog-specialist", 1, AgentLifecycleState.APPROVED)));

        AgentRuntimeDefinitionResolution resolution = new AgentRuntimeDefinitionResolver(
                activationResolver, registry).resolve(KEY);

        assertThat(resolution.reason()).isEqualTo(AgentDefinitionResolutionReason.VERSION_MISMATCH);
        assertThat(resolution.definition()).isNull();
    }

    @Test
    void rejectsAStoredVersionThatHasNotBeenApproved() {
        AgentActivationResolver activationResolver = mock(AgentActivationResolver.class);
        AgentRegistryRepository registry = mock(AgentRegistryRepository.class);
        when(activationResolver.resolve(KEY)).thenReturn(AgentActivationResolution.active("catalog-specialist", 2));
        when(registry.findVersion("catalog-specialist", 2))
                .thenReturn(Optional.of(version("catalog-specialist", 2, AgentLifecycleState.CANDIDATE)));

        AgentRuntimeDefinitionResolution resolution = new AgentRuntimeDefinitionResolver(
                activationResolver, registry).resolve(KEY);

        assertThat(resolution.reason()).isEqualTo(AgentDefinitionResolutionReason.VERSION_NOT_PUBLISHABLE);
        assertThat(resolution.definition()).isNull();
    }

    @Test
    void convertsRegistryErrorsToASafeFallback() {
        AgentActivationResolver activationResolver = mock(AgentActivationResolver.class);
        AgentRegistryRepository registry = mock(AgentRegistryRepository.class);
        when(activationResolver.resolve(KEY)).thenReturn(AgentActivationResolution.active("catalog-specialist", 2));
        when(registry.findVersion("catalog-specialist", 2))
                .thenThrow(new IllegalStateException("database unavailable"));

        AgentRuntimeDefinitionResolution resolution = new AgentRuntimeDefinitionResolver(
                activationResolver, registry).resolve(KEY);

        assertThat(resolution.status()).isEqualTo(AgentDefinitionResolutionStatus.FALLBACK);
        assertThat(resolution.reason()).isEqualTo(AgentDefinitionResolutionReason.REGISTRY_UNAVAILABLE);
        assertThat(resolution.definition()).isNull();
    }

    @Test
    void rejectsInvalidAllowlistValuesAtTheExecutableBoundary() {
        assertThatThrownBy(() -> new AgentRuntimeDefinition(
                "catalog-specialist", 2, "Catalog", "Catalog search", "bedrock", "model",
                AgentInferenceParameters.deterministic(), "system-v1", hash(), "input-v1", "output-v1",
                Set.of(" "), Set.of(), "memory-v1", "response-v1", Duration.ofSeconds(10),
                2, 2_000, 1_000, BigDecimal.valueOf(0.05), "safe-fallback", "eval-v1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowedTools");
    }

    private static AgentVersion version(String agentId, int version, AgentLifecycleState state) {
        boolean approved = state == AgentLifecycleState.APPROVED;
        return new AgentVersion(
                agentId,
                version,
                "Catalog specialist",
                "Search products using deterministic catalog tools",
                state,
                "bedrock",
                "openai.gpt-oss-20b-1:0",
                AgentInferenceParameters.deterministic(),
                "system-v1",
                hash(),
                "catalog-input-v1",
                "catalog-output-v1",
                Set.of("catalog.search"),
                Set.of(),
                "conversation-summary-v1",
                "grounded-customer-support-v1",
                Duration.ofSeconds(10),
                2,
                2_000,
                1_000,
                BigDecimal.valueOf(0.05),
                "safe-fallback",
                "catalog-eval-v1",
                "author",
                CREATED_AT,
                approved ? "reviewer" : null,
                approved ? CREATED_AT.plusSeconds(60) : null);
    }

    private static String hash() {
        return "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    }
}
