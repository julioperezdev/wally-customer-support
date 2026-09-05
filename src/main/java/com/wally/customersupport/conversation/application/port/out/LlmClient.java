package com.wally.customersupport.conversation.application.port.out;

import com.wally.customersupport.conversation.domain.model.ConversationContext;

public interface LlmClient {

    String generateReply(ConversationContext context);
}
