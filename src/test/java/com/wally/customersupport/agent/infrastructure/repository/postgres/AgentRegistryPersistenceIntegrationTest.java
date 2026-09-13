package com.wally.customersupport.agent.infrastructure.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import com.wally.customersupport.agent.application.port.out.AgentRegistryCommandGuard;
import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.domain.model.AgentActivation;
import com.wally.customersupport.agent.domain.model.AgentActivationPolicy;
import com.wally.customersupport.agent.domain.model.AgentActivationRequest;
import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import com.wally.customersupport.agent.domain.model.AgentVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AgentRegistryPersistenceIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-09-07T18:00:00.123456Z");
    private static final Instant APPROVED_AT = Instant.parse("2026-09-07T18:01:00.123456Z");
    private static final String PROMPT_HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("wcs_agent_registry_test")
            .withUsername("wcs")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AgentRegistryRepository registry;

    @Autowired
    private AgentRegistryCommandGuard commandGuard;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsImmutableVersionWithJsonAllowlistsAndReadsLatestApproved() {
        String agentId = "catalog-specialist-" + UUID.randomUUID();
        AgentVersion version = approvedVersion(agentId, 1);

        AgentVersion saved = registry.saveVersion(version);

        assertThat(saved).isEqualTo(version);
        assertThat(registry.findVersion(agentId, 1)).contains(version);
        assertThat(registry.findVersions(agentId)).containsExactly(version);
        assertThat(registry.findLatestVersion(agentId, AgentLifecycleState.APPROVED))
                .contains(version);
        assertThatThrownBy(() -> registry.saveVersion(version))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("immutable");
    }

    @Test
    void exposesAllVersionsAndActivationsForTheReadOnlyRegistryQuery() {
        String agentId = "catalog-specialist-list-" + UUID.randomUUID();
        AgentVersion version = approvedVersion(agentId, 1);
        registry.saveVersion(version);
        AgentActivation activation = new AgentActivation(
                agentId,
                1,
                "prod",
                "telegram",
                "catalog-search",
                "read-only-contract-test",
                100,
                true,
                false,
                null,
                APPROVED_AT,
                "test-operator");
        registry.saveActivation(activation);

        assertThat(registry.findAllVersions()).contains(version);
        assertThat(registry.findAllActivations()).contains(activation);
    }

    @Test
    void updatesLifecycleMetadataWithoutReplacingImmutableVersionContent() {
        String agentId = "catalog-specialist-lifecycle-" + UUID.randomUUID();
        AgentVersion version = approvedVersion(agentId, 1);
        registry.saveVersion(version);

        AgentVersion active = registry.updateLifecycle(
                agentId,
                1,
                AgentLifecycleState.APPROVED,
                AgentLifecycleState.ACTIVE,
                "reviewer",
                APPROVED_AT);

        assertThat(active.state()).isEqualTo(AgentLifecycleState.ACTIVE);
        assertThat(active.name()).isEqualTo(version.name());
        assertThat(active.systemPromptHash()).isEqualTo(version.systemPromptHash());
        assertThat(registry.findVersion(agentId, 1)).contains(active);
    }

    @Test
    void claimsAnAuthoringCommandExactlyOnceWithoutPersistingTheRawKey() {
        String rawKey = "authoring-secret-like-value";

        assertThat(commandGuard.tryAcquire(rawKey)).isTrue();
        assertThat(commandGuard.tryAcquire(rawKey)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from wcs.agent_registry_command_claims", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select key_hash from wcs.agent_registry_command_claims", String.class))
                .hasSize(64)
                .doesNotContain(rawKey);
    }

    @Test
    void persistsActivationAndAKillSwitchShadowsThePreviousActiveReference() {
        String agentId = "catalog-specialist-" + UUID.randomUUID();
        AgentVersion version = approvedVersion(agentId, 1);
        registry.saveVersion(version);

        AgentActivationPolicy policy = new AgentActivationPolicy();
        AgentActivation activation = policy.activate(
                version,
                new AgentActivationRequest(
                        "prod", "telegram", "catalog-search", "initial rollout", 100, true),
                null,
                "operator",
                APPROVED_AT);
        registry.saveActivation(activation);

        assertThat(registry.findActiveActivation(agentId, "prod", "telegram", "catalog-search"))
                .contains(activation);

        AgentActivation stopped = activation.killSwitch("on-call", APPROVED_AT.plusSeconds(30));
        registry.saveActivation(stopped);

        assertThat(registry.findActiveActivation(agentId, "prod", "telegram", "catalog-search"))
                .isEmpty();
    }

    private static AgentVersion approvedVersion(String agentId, int version) {
        Instant createdAt = CREATED_AT.truncatedTo(ChronoUnit.MICROS);
        Instant approvedAt = APPROVED_AT.truncatedTo(ChronoUnit.MICROS);
        return new AgentVersion(
                agentId,
                version,
                "Catalog specialist",
                "Search products using deterministic catalog tools",
                AgentLifecycleState.APPROVED,
                "bedrock",
                "openai.gpt-oss-20b-1:0",
                new AgentInferenceParameters(new BigDecimal("0.000"), new BigDecimal("1.000")),
                "system-v1",
                PROMPT_HASH,
                "catalog-input-v1",
                "catalog-output-v1",
                Set.of("catalog.search", "catalog.stock"),
                Set.of("wcs-catalog-kb"),
                "conversation-summary-v1",
                "grounded-customer-support-v1",
                Duration.ofSeconds(10),
                2,
                2_000,
                1_000,
                new BigDecimal("0.050000"),
                "safe-fallback",
                "catalog-eval-v1",
                "author",
                createdAt,
                "reviewer",
                approvedAt);
    }
}
