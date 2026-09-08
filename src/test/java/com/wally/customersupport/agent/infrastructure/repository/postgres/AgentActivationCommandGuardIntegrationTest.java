package com.wally.customersupport.agent.infrastructure.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import com.wally.customersupport.agent.application.port.out.AgentActivationCommandGuard;
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
class AgentActivationCommandGuardIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("wcs_activation_guard_test")
            .withUsername("wcs")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AgentActivationCommandGuard guard;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void claimsAnActivationCommandExactlyOnceWithoutPersistingTheRawKey() {
        String rawKey = "activation-secret-like-value";

        assertThat(guard.tryAcquire(rawKey)).isTrue();
        assertThat(guard.tryAcquire(rawKey)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from wcs.agent_activation_command_claims", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select key_hash from wcs.agent_activation_command_claims", String.class))
                .hasSize(64)
                .doesNotContain(rawKey);
    }
}
