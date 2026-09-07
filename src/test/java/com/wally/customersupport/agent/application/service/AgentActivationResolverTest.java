package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.domain.model.AgentActivation;
import org.junit.jupiter.api.Test;

class AgentActivationResolverTest {

    private static final AgentActivationKey KEY = new AgentActivationKey(
            "catalog-specialist", "prod", "telegram", "catalog-search");
    private static final Instant ACTIVATED_AT = Instant.parse("2026-09-07T18:00:00Z");

    @Test
    void resolvesAnEnabledActivationToItsExactAgentReference() {
        AgentRegistryRepository registry = mock(AgentRegistryRepository.class);
        when(registry.findLatestActivation(
                KEY.agentId(), KEY.environment(), KEY.channel(), KEY.useCase()))
                .thenReturn(Optional.of(activation(true, false)));

        AgentActivationResolution resolution = new AgentActivationResolver(registry).resolve(KEY);

        assertThat(resolution.status()).isEqualTo(AgentResolutionStatus.ACTIVE);
        assertThat(resolution.reason()).isEqualTo(AgentResolutionReason.ACTIVE);
        assertThat(resolution.agentId()).isEqualTo("catalog-specialist");
        assertThat(resolution.agentVersion()).isEqualTo(2);
    }

    @Test
    void usesNotConfiguredFallbackWhenNoActivationExists() {
        AgentRegistryRepository registry = mock(AgentRegistryRepository.class);
        when(registry.findLatestActivation(
                KEY.agentId(), KEY.environment(), KEY.channel(), KEY.useCase()))
                .thenReturn(Optional.empty());

        AgentActivationResolution resolution = new AgentActivationResolver(registry).resolve(KEY);

        assertThat(resolution.status()).isEqualTo(AgentResolutionStatus.FALLBACK);
        assertThat(resolution.reason()).isEqualTo(AgentResolutionReason.NOT_CONFIGURED);
        assertThat(resolution.agentId()).isNull();
        assertThat(resolution.agentVersion()).isNull();
    }

    @Test
    void neverActivatesAKillSwitchOrDisabledReference() {
        AgentRegistryRepository registry = mock(AgentRegistryRepository.class);
        when(registry.findLatestActivation(
                KEY.agentId(), KEY.environment(), KEY.channel(), KEY.useCase()))
                .thenReturn(Optional.of(activation(false, true)));

        AgentActivationResolution resolution = new AgentActivationResolver(registry).resolve(KEY);

        assertThat(resolution.status()).isEqualTo(AgentResolutionStatus.FALLBACK);
        assertThat(resolution.reason()).isEqualTo(AgentResolutionReason.KILL_SWITCH);
        assertThat(resolution.agentId()).isNull();
        assertThat(resolution.agentVersion()).isNull();
    }

    @Test
    void returnsDisabledForTheLatestDisabledReference() {
        AgentRegistryRepository registry = mock(AgentRegistryRepository.class);
        when(registry.findLatestActivation(
                KEY.agentId(), KEY.environment(), KEY.channel(), KEY.useCase()))
                .thenReturn(Optional.of(activation(false, false)));

        AgentActivationResolution resolution = new AgentActivationResolver(registry).resolve(KEY);

        assertThat(resolution.status()).isEqualTo(AgentResolutionStatus.FALLBACK);
        assertThat(resolution.reason()).isEqualTo(AgentResolutionReason.DISABLED);
    }

    @Test
    void convertsRegistryErrorsToAReferenceFreeFallback() {
        AgentRegistryRepository registry = mock(AgentRegistryRepository.class);
        when(registry.findLatestActivation(
                KEY.agentId(), KEY.environment(), KEY.channel(), KEY.useCase()))
                .thenThrow(new IllegalStateException("database unavailable"));

        AgentActivationResolution resolution = new AgentActivationResolver(registry).resolve(KEY);

        assertThat(resolution.status()).isEqualTo(AgentResolutionStatus.FALLBACK);
        assertThat(resolution.reason()).isEqualTo(AgentResolutionReason.REGISTRY_UNAVAILABLE);
        assertThat(resolution.agentId()).isNull();
        assertThat(resolution.agentVersion()).isNull();
        verify(registry).findLatestActivation(
                KEY.agentId(), KEY.environment(), KEY.channel(), KEY.useCase());
    }

    private static AgentActivation activation(boolean enabled, boolean killSwitch) {
        return new AgentActivation(
                "catalog-specialist",
                2,
                "prod",
                "telegram",
                "catalog-search",
                killSwitch ? "kill switch" : "initial rollout",
                enabled ? 100 : 0,
                enabled,
                killSwitch,
                1,
                ACTIVATED_AT,
                "operator");
    }
}
