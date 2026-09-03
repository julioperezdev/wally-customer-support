package com.wally.customersupport.adapter.out.ai.mock;

import com.wally.customersupport.application.port.out.LlmClient;
import com.wally.customersupport.domain.model.ConversationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wcs.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    private static final String DEFAULT_REPLY = "Gracias por escribirnos. Un agente revisará tu consulta.";

    @Override
    public String generateReply(ConversationContext context) {
        return DEFAULT_REPLY;
    }
}
