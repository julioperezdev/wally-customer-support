package com.wally.customersupport.shared.infrastructure.config;

import java.time.Duration;

import com.wally.customersupport.conversation.domain.model.ConversationMemoryPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wcs.conversation.memory")
public record ConversationMemoryProperties(
        Duration ttl,
        int maxMessages,
        int maxMessageCharacters) {

    public ConversationMemoryPolicy policy() {
        return new ConversationMemoryPolicy(ttl, maxMessages, maxMessageCharacters);
    }
}
