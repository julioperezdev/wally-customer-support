package com.wally.customersupport.agent.domain.model;

import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;

/** Ephemeral execution output used by the evaluation boundary. */
public record AgentEvaluationExecution(
        ResponseHumanizationResult response,
        AgentEvaluationExecutionMetadata metadata,
        ConversationIntentDecision routingDecision) {

    public AgentEvaluationExecution(
            ResponseHumanizationResult response,
            AgentEvaluationExecutionMetadata metadata) {
        this(response, metadata, null);
    }
}
