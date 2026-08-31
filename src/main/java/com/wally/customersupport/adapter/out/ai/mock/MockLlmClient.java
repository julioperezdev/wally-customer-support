package com.wally.customersupport.adapter.out.ai.mock;

import com.wally.customersupport.application.port.out.LlmClient;
import com.wally.customersupport.domain.model.ConversationContext;
import com.wally.customersupport.infrastructure.config.AiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wcs.ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockLlmClient implements LlmClient {

    private final AiProperties properties;

    public MockLlmClient(AiProperties properties) {
        this.properties = properties;
    }

    @Override
    public String generateReply(ConversationContext context) {
        return properties.mockReply();
    }
}
