package com.wally.customersupport.agent.application.evaluation;

import java.util.Objects;

/** Sanitized input for the internal, authenticated evaluation trigger. */
public record AgentEvaluationTriggerExecutionRequest(
        AgentEvaluationTriggerRequest authorizationRequest,
        AgentEvaluationRunRequest evaluationRequest) {

    public AgentEvaluationTriggerExecutionRequest {
        authorizationRequest = Objects.requireNonNull(authorizationRequest, "authorizationRequest");
        evaluationRequest = Objects.requireNonNull(evaluationRequest, "evaluationRequest");
    }
}
