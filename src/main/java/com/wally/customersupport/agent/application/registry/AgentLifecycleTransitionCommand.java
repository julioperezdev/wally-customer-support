package com.wally.customersupport.agent.application.registry;

import java.util.Objects;
import java.util.UUID;

import com.wally.customersupport.agent.domain.model.AgentLifecycleState;

/** Auditable lifecycle transition request for an existing immutable version. */
public record AgentLifecycleTransitionCommand(
        String agentId,
        int version,
        AgentLifecycleState targetState,
        String reason,
        String approvalReference,
        String operationalApprovalReference,
        UUID baselineEvaluationRunId,
        UUID candidateEvaluationRunId) {

    public AgentLifecycleTransitionCommand(
            String agentId,
            int version,
            AgentLifecycleState targetState,
            String reason,
            String approvalReference,
            String operationalApprovalReference) {
        this(agentId, version, targetState, reason, approvalReference, operationalApprovalReference, null, null);
    }

    public AgentLifecycleTransitionCommand {
        agentId = required(agentId, "agentId");
        targetState = Objects.requireNonNull(targetState, "targetState");
        reason = required(reason, "reason");
        approvalReference = normalize(approvalReference);
        operationalApprovalReference = normalize(operationalApprovalReference);
        if ((baselineEvaluationRunId == null) != (candidateEvaluationRunId == null)) {
            throw new IllegalArgumentException("baseline and candidate evaluation run IDs must be supplied together");
        }
        if (baselineEvaluationRunId != null && baselineEvaluationRunId.equals(candidateEvaluationRunId)) {
            throw new IllegalArgumentException("baseline and candidate evaluation run IDs must differ");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
    }

    public boolean hasApprovalReferences() {
        return approvalReference != null && operationalApprovalReference != null;
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
