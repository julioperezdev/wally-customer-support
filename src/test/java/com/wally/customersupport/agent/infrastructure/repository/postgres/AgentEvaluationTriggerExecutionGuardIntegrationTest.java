package com.wally.customersupport.agent.infrastructure.repository.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.wally.customersupport.agent.application.port.out.AgentEvaluationTriggerExecutionGuard;
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
class AgentEvaluationTriggerExecutionGuardIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("wcs_trigger_guard_test")
            .withUsername("wcs")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AgentEvaluationTriggerExecutionGuard guard;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void storesOnlyHashAndRejectsTheSameKeyOnRetry() {
        String key = "trigger-" + UUID.randomUUID();

        assertThat(guard.tryAcquire(key)).isTrue();
        assertThat(guard.tryAcquire(key)).isFalse();

        String storedHash = jdbcTemplate.queryForObject(
                "select key_hash from wcs.agent_evaluation_trigger_claims where key_hash = ?",
                String.class,
                sha256(key));
        assertThat(storedHash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(storedHash).doesNotContain(key);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from wcs.agent_evaluation_trigger_claims where key_hash = ?",
                Integer.class,
                sha256(key))).isEqualTo(1);
    }

    @Test
    void allowsOnlyOneConcurrentClaimForTheSameKey() throws Exception {
        String key = "concurrent-trigger-" + UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(
                    executor.submit(() -> guard.tryAcquire(key)),
                    executor.submit(() -> guard.tryAcquire(key)));

            List<Boolean> results = futures.stream()
                    .map(future -> get(future))
                    .toList();

            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from wcs.agent_evaluation_trigger_claims where key_hash = ?",
                    Integer.class,
                    sha256(key))).isEqualTo(1);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is not available", exception);
        }
    }

    private static boolean get(java.util.concurrent.Future<Boolean> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("concurrent claim did not complete", exception);
        }
    }
}
