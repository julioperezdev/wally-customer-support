package com.wally.customersupport.agent.application.service;

import java.time.Instant;
import java.util.Objects;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionActivationDecision;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionActivationReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionActivationRequest;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionActivationStatus;
import org.springframework.stereotype.Service;

/** Evaluates approval evidence without authenticating, mutating or deleting anything. */
@Service
public class AgentEvaluationRetentionActivationGate {

    public AgentEvaluationRetentionActivationDecision evaluate(
            AgentEvaluationRetentionActivationRequest request,
            Instant evaluatedAt) {
        AgentEvaluationRetentionActivationRequest activationRequest =
                Objects.requireNonNull(request, "request");
        Instant evaluationInstant = Objects.requireNonNull(evaluatedAt, "evaluatedAt");

        if (!activationRequest.requested()) {
            return decision(activationRequest, evaluationInstant,
                    AgentEvaluationRetentionActivationStatus.NOT_REQUESTED,
                    AgentEvaluationRetentionActivationReason.NONE);
        }
        if (isBlank(activationRequest.environment())) {
            return rejected(activationRequest, evaluationInstant,
                    AgentEvaluationRetentionActivationReason.MISSING_ENVIRONMENT);
        }
        if (isBlank(activationRequest.approvedBy())) {
            return rejected(activationRequest, evaluationInstant,
                    AgentEvaluationRetentionActivationReason.MISSING_APPROVER);
        }
        if (isBlank(activationRequest.approvalReference())) {
            return rejected(activationRequest, evaluationInstant,
                    AgentEvaluationRetentionActivationReason.MISSING_APPROVAL_REFERENCE);
        }
        if (activationRequest.approvedAt() == null) {
            return rejected(activationRequest, evaluationInstant,
                    AgentEvaluationRetentionActivationReason.MISSING_APPROVED_AT);
        }
        if (activationRequest.approvedAt().isAfter(evaluationInstant)) {
            return rejected(activationRequest, evaluationInstant,
                    AgentEvaluationRetentionActivationReason.APPROVAL_IN_FUTURE);
        }
        return decision(activationRequest, evaluationInstant,
                AgentEvaluationRetentionActivationStatus.APPROVED_FOR_REVIEW,
                AgentEvaluationRetentionActivationReason.NONE);
    }

    private static AgentEvaluationRetentionActivationDecision rejected(
            AgentEvaluationRetentionActivationRequest request,
            Instant evaluatedAt,
            AgentEvaluationRetentionActivationReason reason) {
        return decision(request, evaluatedAt,
                AgentEvaluationRetentionActivationStatus.REJECTED, reason);
    }

    private static AgentEvaluationRetentionActivationDecision decision(
            AgentEvaluationRetentionActivationRequest request,
            Instant evaluatedAt,
            AgentEvaluationRetentionActivationStatus status,
            AgentEvaluationRetentionActivationReason reason) {
        return new AgentEvaluationRetentionActivationDecision(
                status,
                request.environment(),
                request.approvedBy(),
                request.approvalReference(),
                request.approvedAt(),
                evaluatedAt,
                reason);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
