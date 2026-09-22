package com.wally.customersupport.agent.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.agent.domain.model.AgentEvaluationExecution;
import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.agent.domain.model.AgentInvocationConfiguration;
import com.wally.customersupport.agent.domain.model.AgentLifecyclePolicy;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import com.wally.customersupport.agent.domain.model.AgentVersion;
import com.wally.customersupport.agent.infrastructure.config.AgentEvaluationProperties;
import com.wally.customersupport.conversation.application.port.out.MeasuredLlmClient;
import com.wally.customersupport.conversation.application.port.out.ResponseHumanizer;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import org.junit.jupiter.api.Test;

class BedrockResponsePolicyEvaluationExecutorTest {

    private final MeasuredLlmClient client = mock(MeasuredLlmClient.class);
    private final AgentEvaluationVersionResolver versionResolver = mock(AgentEvaluationVersionResolver.class);
    private final ResponseHumanizer fallback = mock(ResponseHumanizer.class);
    private final BedrockResponsePolicyEvaluationExecutor executor = new BedrockResponsePolicyEvaluationExecutor(
            client,
            versionResolver,
            new AgentEvaluationProperties(
                    "bedrock", 10, 4_000, 512, new BigDecimal("0.05"), Duration.ofSeconds(30)),
            fallback);

    @Test
    void executesWithTheSelectedSqlVersionAndMapsSafeProviderMetadata() {
        AgentVersion version = version();
        when(versionResolver.resolve(any(AgentEvaluationRunRequest.class))).thenAnswer(invocation ->
                invocation.<AgentEvaluationRunRequest>getArgument(0).withVersionDefinition(version));
        when(client.completeMeasuredForAgent(
                anyString(), anyString(), anyString(), any(AgentRuntimeDefinition.class),
                any(Duration.class), isNull()))
                .thenReturn(new MeasuredLlmClient.LlmCompletion(
                        "Encontré estos productos", "bedrock", "model-v2", 42, 37L,
                        100, 40, 140, new BigDecimal("0.000018"), "pricing-v2"));

        AgentEvaluationRunRequest prepared = executor.prepare(request());
        AgentEvaluationExecution execution = executor.execute(
                CatalogResponseEvaluationDataset.scenarios().getFirst(), prepared);

        assertThat(prepared.agentVersion()).isEqualTo("2");
        assertThat(prepared.provider()).isEqualTo("bedrock");
        assertThat(prepared.modelId()).isEqualTo("model-v2");
        assertThat(execution.response().text()).isEqualTo("Encontré estos productos");
        assertThat(execution.response().policyVersion()).isEqualTo("1.1.0");
        assertThat(execution.metadata().agentVersion()).isEqualTo("2");
        assertThat(execution.metadata().provider()).isEqualTo("bedrock");
        assertThat(execution.metadata().modelId()).isEqualTo("model-v2");
        assertThat(execution.metadata().inputTokens()).isEqualTo(100);
        assertThat(execution.metadata().outputTokens()).isEqualTo(40);
        assertThat(execution.metadata().totalTokens()).isEqualTo(140);
        assertThat(execution.metadata().estimatedCostUsd()).isEqualByComparingTo("0.000018");
        verify(client).completeMeasuredForAgent(
                anyString(), anyString(), anyString(), any(AgentRuntimeDefinition.class),
                any(Duration.class), isNull());
    }

    @Test
    void doesNotInvokeBedrockForSafeFallbackScenario() {
        when(versionResolver.resolve(any(AgentEvaluationRunRequest.class))).thenAnswer(invocation ->
                invocation.<AgentEvaluationRunRequest>getArgument(0).withVersionDefinition(version()));
        ResponseHumanizationResult fallbackResult = ResponseHumanizationResult.fallback(
                "No pude preparar una respuesta segura.", "deterministic-response-humanizer", "v1", "INVALID_INPUT");
        when(fallback.humanize(null)).thenReturn(fallbackResult);

        AgentEvaluationExecution execution = executor.execute(
                CatalogResponseEvaluationDataset.scenarios().getLast(), executor.prepare(request()));

        assertThat(execution.response()).isSameAs(fallbackResult);
        assertThat(execution.metadata()).isNull();
        verifyNoInteractions(client);
    }

    @Test
    void ignoresCallerProviderAndModelAndUsesTheImmutableSqlVersion() {
        AgentVersion version = version();
        when(versionResolver.resolve(any(AgentEvaluationRunRequest.class))).thenAnswer(invocation ->
                invocation.<AgentEvaluationRunRequest>getArgument(0).withVersionDefinition(version));
        AgentEvaluationRunRequest request = new AgentEvaluationRunRequest(
                CatalogResponseEvaluationDataset.VERSION,
                "response-humanization",
                "1.1.0",
                "mock",
                "client-selected-model");

        AgentEvaluationRunRequest prepared = executor.prepare(request);
        executor.validate(prepared);

        assertThat(prepared.agentVersion()).isEqualTo("2");
        assertThat(prepared.provider()).isEqualTo("bedrock");
        assertThat(prepared.modelId()).isEqualTo("model-v2");
    }

    private static AgentEvaluationRunRequest request() {
        return new AgentEvaluationRunRequest(
                CatalogResponseEvaluationDataset.VERSION,
                "response-humanization",
                "2");
    }

    private static AgentVersion version() {
        String systemPrompt = "Humanize only the approved synthetic catalog facts.";
        AgentInvocationConfiguration invocation = new AgentInvocationConfiguration(
                systemPrompt,
                "<use_case>{{use_case}}</use_case><channel>{{channel}}</channel>"
                        + "<approved_knowledge>{{approved_knowledge}}</approved_knowledge>"
                        + "<required_facts>{{required_facts}}</required_facts>",
                "{\"type\":\"object\"}",
                "{\"type\":\"string\"}",
                "medium",
                false,
                "pricing-v2",
                new BigDecimal("0.1"),
                new BigDecimal("0.2"));
        AgentVersion draft = AgentVersion.draft(
                "response-humanization",
                2,
                "1.1.0",
                "Response humanization candidate",
                "Humanizes catalog results using only approved facts",
                "bedrock",
                "model-v2",
                new AgentInferenceParameters(new BigDecimal("0.2"), new BigDecimal("0.9")),
                "system-v2",
                AgentInvocationConfiguration.sha256(systemPrompt.trim()),
                "input-v2",
                "output-v1",
                Set.of(),
                Set.of(),
                "memory-v1",
                "response-policy-v1",
                Duration.ofSeconds(20),
                1,
                4_000,
                512,
                new BigDecimal("0.05"),
                null,
                CatalogResponseEvaluationDataset.VERSION,
                invocation,
                "author",
                Instant.parse("2026-09-22T12:00:00Z"));
        return new AgentLifecyclePolicy().transition(
                draft,
                AgentLifecycleState.CANDIDATE,
                "author",
                Instant.parse("2026-09-22T12:01:00Z"));
    }
}
