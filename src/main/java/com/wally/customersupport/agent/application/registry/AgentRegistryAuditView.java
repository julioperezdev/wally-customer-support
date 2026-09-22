package com.wally.customersupport.agent.application.registry;

import java.time.Instant;
import java.util.UUID;

public record AgentRegistryAuditView(
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

    public AgentRegistryAuditView(
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
        this(operation, agentId, agentVersion, previousState, resultingState, environment, channel,
                useCase, actorId, reason, occurredAt, null, null, null, null);
    }
}
