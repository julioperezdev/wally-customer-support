package com.wally.customersupport.conversation.infrastructure.ai.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wally.customersupport.shared.infrastructure.config.PromptManagementProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.bedrockagent.BedrockAgentClient;
import software.amazon.awssdk.services.bedrockagent.model.GetPromptRequest;
import software.amazon.awssdk.services.bedrockagent.model.GetPromptResponse;
import software.amazon.awssdk.services.bedrockagent.model.PromptTemplateConfiguration;
import software.amazon.awssdk.services.bedrockagent.model.PromptVariant;

class BedrockPromptRegistryTest {

    @Test
    void loadsTheConfiguredImmutableVersionAndDefaultTextVariant() {
        BedrockAgentClient client = mock(BedrockAgentClient.class);
        when(client.getPrompt(any(GetPromptRequest.class))).thenReturn(GetPromptResponse.builder()
                .version("7")
                .defaultVariant("approved")
                .variants(
                        variant("draft-like", "should not be selected"),
                        variant("approved", "System prompt from Bedrock"))
                .build());
        BedrockPromptRegistry registry = new BedrockPromptRegistry(
                client,
                new PromptManagementProperties("intent-prompt", "7", "response-prompt", "3"));

        PromptDefinition definition = registry.intentPrompt("conversation-intent-v2");

        assertEquals("conversation-intent", definition.id());
        assertEquals("7", definition.version());
        assertEquals("System prompt from Bedrock", definition.content());
        assertEquals(64, definition.sha256().length());
        ArgumentCaptor<GetPromptRequest> request = ArgumentCaptor.forClass(GetPromptRequest.class);
        verify(client).getPrompt(request.capture());
        assertEquals("intent-prompt", request.getValue().promptIdentifier());
        assertEquals("7", request.getValue().promptVersion());
    }

    @Test
    void failsClosedWhenThePromptDoesNotContainAUsableTextVariant() {
        BedrockAgentClient client = mock(BedrockAgentClient.class);
        when(client.getPrompt(any(GetPromptRequest.class))).thenReturn(GetPromptResponse.builder()
                .version("3")
                .defaultVariant("approved")
                .variants(variant("approved", " "))
                .build());
        BedrockPromptRegistry registry = new BedrockPromptRegistry(
                client,
                new PromptManagementProperties("intent-prompt", "7", "response-prompt", "3"));

        assertThrows(IllegalStateException.class, () -> registry.responsePrompt("conversation-response-v1"));
    }

    private static PromptVariant variant(String name, String text) {
        return PromptVariant.builder()
                .name(name)
                .templateConfiguration(PromptTemplateConfiguration.fromText(builder -> builder.text(text)))
                .build();
    }
}
