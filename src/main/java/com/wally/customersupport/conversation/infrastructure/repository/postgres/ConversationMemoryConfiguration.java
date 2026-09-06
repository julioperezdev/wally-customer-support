package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import com.wally.customersupport.conversation.domain.model.ConversationMemoryPolicy;
import com.wally.customersupport.shared.infrastructure.config.ConversationMemoryProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConversationMemoryConfiguration {

    @Bean
    ConversationMemoryPolicy conversationMemoryPolicy(ConversationMemoryProperties properties) {
        return properties.policy();
    }
}
