package com.wally.customersupport.agent.infrastructure.http;

import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerExecutionResult;

/** Sanitized HTTP output for an evaluation trigger attempt. */
public record AgentEvaluationTriggerHttpResponse(
        String status,
        String reason,
        UUID runId) {

    public static AgentEvaluationTriggerHttpResponse from(
            AgentEvaluationTriggerExecutionResult result) {
        return new AgentEvaluationTriggerHttpResponse(
                result.status().name(),
                result.reason().name(),
                result.runId());
    }
}
