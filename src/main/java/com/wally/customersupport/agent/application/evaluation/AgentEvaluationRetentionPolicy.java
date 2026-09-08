package com.wally.customersupport.agent.application.evaluation;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Explicit retention policy for completed evaluation evidence. */
public record AgentEvaluationRetentionPolicy(Duration completedRunRetention) {

    public static final Duration RECOMMENDED_COMPLETED_RUN_RETENTION = Duration.ofDays(90);

    public AgentEvaluationRetentionPolicy {
        completedRunRetention = Objects.requireNonNull(completedRunRetention, "completedRunRetention");
        if (completedRunRetention.isZero() || completedRunRetention.isNegative()) {
            throw new IllegalArgumentException("completedRunRetention must be positive");
        }
    }

    public static AgentEvaluationRetentionPolicy recommended() {
        return new AgentEvaluationRetentionPolicy(RECOMMENDED_COMPLETED_RUN_RETENTION);
    }

    public Instant expiresAt(Instant completedAt) {
        return Objects.requireNonNull(completedAt, "completedAt").plus(completedRunRetention);
    }
}
