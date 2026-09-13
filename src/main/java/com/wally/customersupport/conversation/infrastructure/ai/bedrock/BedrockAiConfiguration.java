package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import com.wally.customersupport.shared.infrastructure.config.AiProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagent.BedrockAgentClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration
@ConditionalOnProperty(name = "wcs.ai.provider", havingValue = "bedrock")
public class BedrockAiConfiguration {

    @Bean(destroyMethod = "close")
    BedrockRuntimeClient bedrockRuntimeClient(AiProperties properties) {
        return BedrockRuntimeClient.builder()
                .region(Region.of(properties.effectiveRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(configuration -> configuration.apiCallTimeout(properties.effectiveRequestTimeout()))
                .build();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "wcs.ai.prompt.provider", havingValue = "bedrock")
    BedrockAgentClient bedrockPromptClient(AiProperties properties) {
        return BedrockAgentClient.builder()
                .region(Region.of(properties.effectiveRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(configuration -> configuration.apiCallTimeout(properties.effectiveRequestTimeout()))
                .build();
    }

    @Bean
    BedrockConverseClient bedrockConverseClient(BedrockRuntimeClient client, AiProperties properties) {
        return new BedrockConverseClient(client, properties);
    }
}
