package com.wally.customersupport.adapter.out.knowledge.bedrock;

import com.wally.customersupport.infrastructure.config.RagProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;

@Configuration
@ConditionalOnProperty(name = "wcs.rag.provider", havingValue = "bedrock-kb")
public class BedrockKnowledgeConfiguration {

    @Bean(destroyMethod = "close")
    BedrockAgentRuntimeClient bedrockAgentRuntimeClient(RagProperties properties) {
        return BedrockAgentRuntimeClient.builder()
                .region(Region.of(properties.effectiveRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    BedrockKnowledgeRetriever bedrockKnowledgeRetriever(
            BedrockAgentRuntimeClient client,
            RagProperties properties) {
        return new BedrockKnowledgeRetriever(client, properties);
    }
}
