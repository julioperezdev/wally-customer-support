package com.wally.customersupport.conversation.infrastructure.ai.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

import com.wally.customersupport.agent.application.service.AgentActivationKey;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinitionResolution;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinitionResolver;
import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.agent.domain.model.AgentInvocationConfiguration;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.shared.infrastructure.config.AgentRuntimeProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class BedrockAgentProfileResolverTest {

    private static final String SYSTEM_PROMPT = "Use approved knowledge only.";

    @Test
    void resolvesTheCurrentSqlBackedProfileAndValidatesItsContract() {
        AgentRuntimeDefinitionResolver definitions = mock(AgentRuntimeDefinitionResolver.class);
        AgentRuntimeDefinition definition = definition(SYSTEM_PROMPT);
        when(definitions.resolve(any(AgentActivationKey.class)))
                .thenReturn(AgentRuntimeDefinitionResolution.active(definition));
        BedrockAgentProfileResolver resolver = resolver(definitions, true);

        assertThat(resolver.resolve("response-generation", "GENERAL_SUPPORT", Channel.TELEGRAM))
                .contains(definition);
        verify(definitions).resolve(new AgentActivationKey(
                "response-generation", "prod", "telegram", "GENERAL_SUPPORT"));
    }

    @Test
    void rejectsAProfileWhosePromptHashDoesNotMatchBeforeItReachesBedrock() {
        AgentRuntimeDefinitionResolver definitions = mock(AgentRuntimeDefinitionResolver.class);
        when(definitions.resolve(any(AgentActivationKey.class)))
                .thenReturn(AgentRuntimeDefinitionResolution.active(definition("tampered prompt")));

        assertThat(resolver(definitions, true).resolve("response-generation", "GENERAL_SUPPORT", Channel.TELEGRAM))
                .isEmpty();
    }

    @Test
    void doesNotReadSqlProfilesWhenTheRuntimeGateIsDisabled() {
        AgentRuntimeDefinitionResolver definitions = mock(AgentRuntimeDefinitionResolver.class);

        assertThat(resolver(definitions, false).resolve("response-generation", "GENERAL_SUPPORT", Channel.TELEGRAM))
                .isEmpty();
        verify(definitions, org.mockito.Mockito.never()).resolve(any(AgentActivationKey.class));
    }

    private static BedrockAgentProfileResolver resolver(
            AgentRuntimeDefinitionResolver definitions,
            boolean activationEnabled) {
        return new BedrockAgentProfileResolver(
                definitions,
                new AgentRuntimeProperties(activationEnabled, "prod", false, null, "noop", "test", 0),
                new ObjectMapper());
    }

    private static AgentRuntimeDefinition definition(String prompt) {
        return new AgentRuntimeDefinition(
                "response-generation", 1, "Response generation", "Grounded general support", "bedrock",
                "openai.gpt-oss-20b-1:0", new AgentInferenceParameters(BigDecimal.ZERO, new BigDecimal("0.9")),
                "response-generation-v1", AgentInvocationConfiguration.sha256(SYSTEM_PROMPT),
                "response-input-v1", "response-output-v1", Set.of(), Set.of(), "summary-v1", "grounded-v1",
                Duration.ofSeconds(10), 1, 500, 1024, new BigDecimal("0.01"), null, "response-eval-v1",
                "1.0.0", new AgentInvocationConfiguration(
                        prompt, "{{latest_message}}", "{}", "{}", "medium", false,
                        "aws-bedrock-test-v1", new BigDecimal("0.07"), new BigDecimal("0.30")));
    }
}
