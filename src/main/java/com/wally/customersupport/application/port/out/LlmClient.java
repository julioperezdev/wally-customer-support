package com.wally.customersupport.application.port.out;

import com.wally.customersupport.domain.model.ConversationContext;

public interface LlmClient {

    String generateReply(ConversationContext context);
}
