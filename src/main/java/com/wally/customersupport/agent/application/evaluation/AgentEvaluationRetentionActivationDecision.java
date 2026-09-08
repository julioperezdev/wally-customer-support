package com.wally.customersupport.agent.application.evaluation;

import java.time.Instant;
import java.util.Objects;

/** Sanitized result of the retention activation gate. */
public record AgentEvaluationRetentionActivationDecision(
        AgentEvaluationRetentionActivationStatus status,
        String environment,
        String approvedBy,
        String approvalReference,
        Instant approvedAt,
        Instant evaluatedAt,
        AgentEvaluationRetentionActivationReason reason) {

    public AgentEvaluationRetentionActivationDecision {
        status = Objects.requireNonNull(status, "status");
        evaluatedAt = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        reason = Objects.requireNonNull(reason, "reason");
        if (status == AgentEvaluationRetentionActivationStatus.NOT_REQUESTED
                && reason != AgentEvaluationRetentionActivationReason.NONE) {
            throw new IllegalArgumentException("not requested decision must have no rejection reason");
        }
        if (status == AgentEvaluationRetentionActivationStatus.APPROVED_FOR_REVIEW
                && reason != AgentEvaluationRetentionActivationReason.NONE) {
            throw new IllegalArgumentException("approved decision must have no rejection reason");
        }
        if (status == AgentEvaluationRetentionActivationStatus.REJECTED
                && reason == AgentEvaluationRetentionActivationReason.NONE) {
            throw new IllegalArgumentException("rejected decision must have a reason");
        }
    }

    public boolean approvedForReview() {
        return status == AgentEvaluationRetentionActivationStatus.APPROVED_FOR_REVIEW;
    }
}
