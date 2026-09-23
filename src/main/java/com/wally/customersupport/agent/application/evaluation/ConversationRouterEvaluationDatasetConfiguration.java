package com.wally.customersupport.agent.application.evaluation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.ObjectMapper;

/** Registers immutable router evaluation suite versions independently in the dataset catalog. */
@Configuration(proxyBeanMethods = false)
class ConversationRouterEvaluationDatasetConfiguration {

    @Bean
    AgentEvaluationDataset conversationRouterV1EvaluationDataset(ObjectMapper objectMapper) {
        return new ConversationRouterEvaluationDatasetProvider(
                objectMapper, ConversationRouterEvaluationDatasetProvider.VERSION);
    }

    @Bean
    AgentEvaluationDataset conversationRouterV2EvaluationDataset(ObjectMapper objectMapper) {
        return new ConversationRouterEvaluationDatasetProvider(
                objectMapper, ConversationRouterEvaluationDatasetProvider.VERSION_2);
    }
}
