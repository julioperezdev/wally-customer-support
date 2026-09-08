package com.wally.customersupport.agent.application.evaluation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Bounded, sanitized review of retention decisions for one history page. */
public record AgentEvaluationRetentionReview(
        Instant evaluatedAt,
        AgentEvaluationRetentionPolicy policy,
        List<AgentEvaluationRetentionDecision> decisions,
        int activeCount,
        int expiredCount) {

    public AgentEvaluationRetentionReview {
        evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        policy = Objects.requireNonNull(policy, "policy");
        decisions = decisions == null ? List.of() : decisions.stream()
                .map(decision -> Objects.requireNonNull(decision, "decisions must not contain null"))
                .toList();
        if (activeCount < 0 || expiredCount < 0
                || activeCount + expiredCount != decisions.size()
                || activeCount != (int) decisions.stream()
                        .filter(decision -> decision.status() == AgentEvaluationRetentionStatus.ACTIVE)
                        .count()
                || expiredCount != (int) decisions.stream()
                        .filter(AgentEvaluationRetentionDecision::expired)
                        .count()) {
            throw new IllegalArgumentException("retention counts are inconsistent");
        }
    }

    public int totalRuns() {
        return decisions.size();
    }
}
