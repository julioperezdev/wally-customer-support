package com.wally.customersupport.agent.domain.model;

import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;

/** Ephemeral execution output used by the evaluation boundary. */
public record AgentEvaluationExecution(
        ResponseHumanizationResult response,
        AgentEvaluationExecutionMetadata metadata) {
}
