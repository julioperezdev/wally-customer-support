package com.wally.customersupport.conversation.application.port.out;

import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.conversation.domain.model.ConversationContext;

public interface LlmClient {

    String generateReply(ConversationContext context);

    /**
     * Generates a reply using an immutable, already validated agent snapshot.
     *
     * <p>The default preserves compatibility for providers that have not yet
     * implemented versioned execution. Provider adapters that support the
     * control-plane contract must override this method and apply the model,
     * prompt and inference values from the definition.</p>
     */
    default String generateReply(
            ConversationContext context,
            AgentRuntimeDefinition definition) {
        return generateReply(context);
    }
}
