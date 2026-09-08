package com.wally.customersupport.agent.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;

import com.wally.customersupport.agent.domain.model.AgentEvaluationExecution;
import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import com.wally.customersupport.agent.infrastructure.config.AgentEvaluationProperties;
import com.wally.customersupport.conversation.application.port.out.MeasuredLlmClient;
import com.wally.customersupport.conversation.application.port.out.ResponseHumanizer;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import com.wally.customersupport.shared.infrastructure.config.AiProperties;
import org.junit.jupiter.api.Test;

class BedrockResponsePolicyEvaluationExecutorTest {

    private final MeasuredLlmClient client = mock(MeasuredLlmClient.class);
    private final ResponseHumanizer fallback = mock(ResponseHumanizer.class);
    private final BedrockResponsePolicyEvaluationExecutor executor = new BedrockResponsePolicyEvaluationExecutor(
            client,
            new AiProperties(
                    "bedrock",
                    "model-v1",
                    "us-east-1",
                    "pricing-v1",
                    new BigDecimal("0.1"),
                    new BigDecimal("0.2")),
            new AgentEvaluationProperties(
                    "bedrock", 10, 4_000, 512, new BigDecimal("0.05"), Duration.ofSeconds(30)),
            fallback);

    @Test
    void mapsProviderCompletionToSanitizedEvaluationMetadata() {
        when(client.completeMeasured(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyFloat()))
                .thenReturn(new MeasuredLlmClient.LlmCompletion(
                        "Encontré estos productos", "bedrock", "model-v1", 42, 37L,
                        100, 40, 140, new BigDecimal("0.000018"), "pricing-v1"));

        AgentEvaluationRunRequest request = new AgentEvaluationRunRequest(
                CatalogResponseEvaluationDataset.VERSION,
                "catalog-specialist",
                "v2",
                "bedrock",
                "model-v1");
        AgentEvaluationScenario scenario = CatalogResponseEvaluationDataset.scenarios().getFirst();

        AgentEvaluationExecution execution = executor.execute(scenario, request);

        assertThat(execution.response().text()).isEqualTo("Encontré estos productos");
        assertThat(execution.metadata().provider()).isEqualTo("bedrock");
        assertThat(execution.metadata().modelId()).isEqualTo("model-v1");
        assertThat(execution.metadata().inputTokens()).isEqualTo(100);
        assertThat(execution.metadata().outputTokens()).isEqualTo(40);
        assertThat(execution.metadata().totalTokens()).isEqualTo(140);
        assertThat(execution.metadata().estimatedCostUsd()).isEqualByComparingTo("0.000018");
    }

    @Test
    void doesNotInvokeBedrockForSafeFallbackScenario() {
        ResponseHumanizationResult fallbackResult = ResponseHumanizationResult.fallback(
                "No pude preparar una respuesta segura.", "deterministic-response-humanizer", "v1", "INVALID_INPUT");
        when(fallback.humanize(null)).thenReturn(fallbackResult);

        AgentEvaluationExecution execution = executor.execute(
                CatalogResponseEvaluationDataset.scenarios().getLast(),
                new AgentEvaluationRunRequest(
                        CatalogResponseEvaluationDataset.VERSION,
                        "catalog-specialist",
                        "v2",
                        "bedrock",
                        "model-v1"));

        assertThat(execution.response()).isSameAs(fallbackResult);
        assertThat(execution.metadata()).isNull();
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    void rejectsProviderOrModelMismatchBeforeCallingProvider() {
        AgentEvaluationRunRequest request = new AgentEvaluationRunRequest(
                CatalogResponseEvaluationDataset.VERSION,
                "catalog-specialist",
                "v2",
                "mock",
                "model-v1");

        assertThatThrownBy(() -> executor.validate(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("evaluation provider is not supported by bedrock executor");

        assertThatThrownBy(() -> executor.validate(new AgentEvaluationRunRequest(
                CatalogResponseEvaluationDataset.VERSION,
                "catalog-specialist",
                "v2",
                "bedrock",
                "other-model")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("evaluation model does not match configured model");
    }
}
