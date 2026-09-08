package com.wally.customersupport.agent.application.evaluation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Sanitized retention decision for one completed evaluation run. */
public record AgentEvaluationRetentionDecision(
        UUID runId,
        Instant completedAt,
        Instant expiresAt,
        AgentEvaluationRetentionStatus status) {

    public AgentEvaluationRetentionDecision {
        runId = Objects.requireNonNull(runId, "runId");
        completedAt = Objects.requireNonNull(completedAt, "completedAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        status = Objects.requireNonNull(status, "status");
        if (expiresAt.isBefore(completedAt)) {
            throw new IllegalArgumentException("expiresAt must not be before completedAt");
        }
    }

    public boolean expired() {
        return status == AgentEvaluationRetentionStatus.EXPIRED;
    }
}
