package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionPolicy;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionStatus;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRun;
import com.wally.customersupport.agent.domain.model.AgentEvaluationResult;
import com.wally.customersupport.agent.domain.model.AgentEvaluationSuiteResult;
import org.junit.jupiter.api.Test;

class AgentEvaluationRetentionPolicyServiceTest {

    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final Instant COMPLETED_AT = Instant.parse("2026-09-08T00:00:00Z");

    @Test
    void calculatesActiveAndExpiredStatesAtTheExactBoundary() {
        var policy = new AgentEvaluationRetentionPolicy(Duration.ofDays(90));
        var service = new AgentEvaluationRetentionPolicyService();
        AgentEvaluationRun run = run();
        Instant expiresAt = COMPLETED_AT.plus(Duration.ofDays(90));

        var active = service.evaluate(run, policy, expiresAt.minusNanos(1));
        var expiredAtBoundary = service.evaluate(run, policy, expiresAt);
        var expiredAfterBoundary = service.evaluate(run, policy, expiresAt.plusNanos(1));

        assertThat(active.status()).isEqualTo(AgentEvaluationRetentionStatus.ACTIVE);
        assertThat(active.expired()).isFalse();
        assertThat(expiredAtBoundary.status()).isEqualTo(AgentEvaluationRetentionStatus.EXPIRED);
        assertThat(expiredAfterBoundary.expired()).isTrue();
        assertThat(expiredAtBoundary.expiresAt()).isEqualTo(expiresAt);
        assertThat(expiredAtBoundary.runId()).isEqualTo(RUN_ID);
        assertThat(expiredAtBoundary.toString()).doesNotContain("respuesta de prueba");
    }

    @Test
    void exposesTheRecommendedPolicyWithoutActivatingIt() {
        assertThat(AgentEvaluationRetentionPolicy.recommended().completedRunRetention())
                .isEqualTo(Duration.ofDays(90));
    }

    @Test
    void rejectsMissingOrNonPositivePolicyDurations() {
        assertThatThrownBy(() -> new AgentEvaluationRetentionPolicy(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("completedRunRetention must be positive");
        assertThatThrownBy(() -> new AgentEvaluationRetentionPolicy(Duration.ofDays(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("completedRunRetention must be positive");
        assertThatThrownBy(() -> new AgentEvaluationRetentionPolicy(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("completedRunRetention");
    }

    private static AgentEvaluationRun run() {
        String datasetVersion = "retention-dataset-v1";
        AgentEvaluationResult scenario = new AgentEvaluationResult(
                "scenario-1", datasetVersion, true, 1.0,
                List.of(), null);
        return new AgentEvaluationRun(
                RUN_ID,
                datasetVersion,
                "catalog-specialist",
                "v1",
                "mock",
                "deterministic-v1",
                COMPLETED_AT.minusSeconds(1),
                COMPLETED_AT,
                1,
                new AgentEvaluationSuiteResult(
                        datasetVersion,
                        List.of(scenario),
                        1,
                        1,
                        0,
                        1.0,
                        1.0,
                        Map.of()));
    }
}
