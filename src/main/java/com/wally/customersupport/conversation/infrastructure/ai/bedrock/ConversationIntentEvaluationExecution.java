package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import java.util.Objects;

import com.wally.customersupport.conversation.application.port.out.MeasuredLlmClient;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;

/** Ephemeral, measured result of executing one pinned router profile in evaluation mode. */
public record ConversationIntentEvaluationExecution(
        ConversationIntentDecision decision,
        MeasuredLlmClient.LlmCompletion completion) {

    public ConversationIntentEvaluationExecution {
        decision = Objects.requireNonNull(decision, "decision");
        completion = Objects.requireNonNull(completion, "completion");
    }
}
