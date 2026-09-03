package com.wally.customersupport.adapter.out.ai.bedrock;

import java.util.stream.Collectors;

import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

final class BedrockConverseClient {

    private final BedrockRuntimeClient client;
    private final String modelId;

    BedrockConverseClient(BedrockRuntimeClient client, String modelId) {
        this.client = client;
        this.modelId = modelId;
    }

    String complete(String systemPrompt, String userPrompt, int maxTokens, float temperature) {
        Message message = Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.fromText(userPrompt))
                .build();
        ConverseRequest request = ConverseRequest.builder()
                .modelId(modelId)
                .system(SystemContentBlock.fromText(systemPrompt))
                .messages(message)
                .inferenceConfig(InferenceConfiguration.builder()
                        .maxTokens(maxTokens)
                        .temperature(temperature)
                        .topP(0.9f)
                        .build())
                .build();

        var response = client.converse(request);
        if (response.output() == null || response.output().message() == null) {
            throw new IllegalStateException("Bedrock did not return a message");
        }
        String text = response.output().message().content().stream()
                .map(ContentBlock::text)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining("\n"))
                .trim();
        if (text.isBlank()) {
            throw new IllegalStateException("Bedrock returned an empty message");
        }
        return text;
    }
}
