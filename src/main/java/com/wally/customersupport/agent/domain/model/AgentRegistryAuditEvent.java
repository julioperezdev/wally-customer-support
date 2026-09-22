package com.wally.customersupport.agent.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Content-free audit evidence for changes to the agent control plane.
 *
 * <p>Approval values, prompts and conversation content are intentionally not
 * stored here. The event records who changed what, why and where.</p>
 */
public record AgentRegistryAuditEvent(
        String operation,
        String agentId,
        Integer agentVersion,
        String previousState,
        String resultingState,
        String environment,
        String channel,
        String useCase,
        String actorId,
        String reason,
        Instant occurredAt,
        UUID baselineEvaluationRunId,
        UUID candidateEvaluationRunId,
        String evaluationDatasetVersion,
        String evaluationAssessmentOutcome) {

    public AgentRegistryAuditEvent(
            String operation,
            String agentId,
            Integer agentVersion,
            String previousState,
            String resultingState,
            String environment,
            String channel,
            String useCase,
            String actorId,
            String reason,
            Instant occurredAt) {
        this(operation, agentId, agentVersion, previousState, resultingState, environment, channel, useCase,
                actorId, reason, occurredAt, null, null, null, null);
    }

    public AgentRegistryAuditEvent {
        operation = required(operation, "operation");
        agentId = required(agentId, "agentId");
        previousState = optional(previousState);
        resultingState = optional(resultingState);
        environment = optional(environment);
        channel = optional(channel);
        useCase = optional(useCase);
        actorId = required(actorId, "actorId");
        reason = required(reason, "reason");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        evaluationDatasetVersion = optional(evaluationDatasetVersion);
        evaluationAssessmentOutcome = optional(evaluationAssessmentOutcome);
        boolean evaluationEvidenceAbsent = baselineEvaluationRunId == null
                && candidateEvaluationRunId == null
                && evaluationDatasetVersion == null
                && evaluationAssessmentOutcome == null;
        boolean evaluationEvidenceComplete = baselineEvaluationRunId != null
                && candidateEvaluationRunId != null
                && evaluationDatasetVersion != null
                && evaluationAssessmentOutcome != null
                && !baselineEvaluationRunId.equals(candidateEvaluationRunId);
        if (!evaluationEvidenceAbsent && !evaluationEvidenceComplete) {
            throw new IllegalArgumentException("evaluation evidence must be complete or absent");
        }
        if (agentVersion != null && agentVersion < 1) {
            throw new IllegalArgumentException("agentVersion must be positive");
        }
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
