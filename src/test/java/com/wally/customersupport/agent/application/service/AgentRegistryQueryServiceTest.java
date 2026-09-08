package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.application.registry.AgentRegistryQuery;
import com.wally.customersupport.agent.domain.model.AgentActivation;
import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import com.wally.customersupport.agent.domain.model.AgentVersion;
import org.junit.jupiter.api.Test;

class AgentRegistryQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void mapsVersionsAndActivationsToAStableSanitizedReadModel() {
        AgentRegistryRepository repository = mock(AgentRegistryRepository.class);
        AgentVersion version = version("catalog-specialist", 1);
        AgentActivation activation = new AgentActivation(
                "catalog-specialist", 1, "prod", "telegram", "catalog-search",
                "initial rollout", 100, true, false, null, NOW, "operator");
        when(repository.findAllVersions()).thenReturn(List.of(version));
        when(repository.findAllActivations()).thenReturn(List.of(activation));

        var result = new AgentRegistryQueryService(repository).search(
                new AgentRegistryQuery("catalog-specialist", "prod", "telegram", "catalog-search", 10));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().versions().getFirst().systemPromptHash()).isEqualTo(HASH);
        assertThat(result.getFirst().versions().getFirst()).hasNoNullFieldsOrPropertiesExcept(
                "fallbackAgentId", "approvedAt");
        assertThat(result.getFirst().activations().getFirst().activatedAt()).isEqualTo(NOW);
    }

    @Test
    void filtersActivationDimensionsAndKeepsTheResponseBoundedByAgent() {
        AgentRegistryRepository repository = mock(AgentRegistryRepository.class);
        when(repository.findAllVersions()).thenReturn(List.of(version("a", 1), version("b", 1)));
        when(repository.findAllActivations()).thenReturn(List.of(
                new AgentActivation("a", 1, "prod", "telegram", "catalog-search", "rollout", 100, true, false, null, NOW, "operator"),
                new AgentActivation("b", 1, "test", "telegram", "catalog-search", "rollout", 100, true, false, null, NOW, "operator")));

        var result = new AgentRegistryQueryService(repository).search(
                new AgentRegistryQuery(null, "prod", "telegram", "catalog-search", 1));

        assertThat(result).extracting("agentId").containsExactly("a");
    }

    private static AgentVersion version(String agentId, int version) {
        return new AgentVersion(
                agentId, version, "Catalog specialist", "Searches the catalog", AgentLifecycleState.APPROVED,
                "bedrock", "model-v1", new AgentInferenceParameters(BigDecimal.ZERO, BigDecimal.ONE),
                "system-v1", HASH, "input-v1", "output-v1", Set.of("catalog.search"), Set.of("catalog-kb"),
                "summary-v1", "response-v1", Duration.ofSeconds(10), 2, 2_000, 1_000,
                new BigDecimal("0.050000"), null, "eval-v1", "author", NOW, "reviewer", NOW);
    }
}
