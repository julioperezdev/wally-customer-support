package com.wally.customersupport.agent.application.evaluation;

import java.util.Objects;
import java.util.UUID;

/** Sanitized result of an internal evaluation trigger attempt. */
public record AgentEvaluationTriggerExecutionResult(
        AgentEvaluationTriggerExecutionStatus status,
        AgentEvaluationTriggerExecutionReason reason,
        AgentEvaluationTriggerAuthorizationReason authorizationReason,
        UUID runId) {

    public AgentEvaluationTriggerExecutionResult {
        status = Objects.requireNonNull(status, "status");
        reason = Objects.requireNonNull(reason, "reason");
        if (status == AgentEvaluationTriggerExecutionStatus.DENIED) {
            Objects.requireNonNull(authorizationReason, "authorizationReason");
        } else if (authorizationReason != null) {
            throw new IllegalArgumentException("authorizationReason is only valid for denied executions");
        }
        if (status == AgentEvaluationTriggerExecutionStatus.COMPLETED) {
            Objects.requireNonNull(runId, "runId");
        } else if (runId != null) {
            throw new IllegalArgumentException("runId is only valid for completed executions");
        }
    }

    public boolean completed() {
        return status == AgentEvaluationTriggerExecutionStatus.COMPLETED;
    }
}
