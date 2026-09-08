package com.wally.customersupport.agent.infrastructure.shadow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

import com.wally.customersupport.agent.application.shadow.AgentShadowExecutionRequest;
import com.wally.customersupport.agent.application.shadow.AgentShadowExecutionResult;
import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.conversation.application.port.out.MeasuredLlmClient;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.shared.infrastructure.config.AiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class BedrockAgentShadowExecutorTest {

    @Mock
    private ObjectProvider<MeasuredLlmClient> clientProvider;
    @Mock
    private MeasuredLlmClient client;

    @Test
    void mapsMeasuredBedrockCompletionToSanitizedShadowMetadata() {
        when(clientProvider.getIfAvailable()).thenReturn(client);
        when(client.completeMeasured(
                any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyFloat()))
                .thenReturn(new MeasuredLlmClient.LlmCompletion(
                        "Respuesta candidata", "bedrock", "model-v1", 42, 30L,
                        100, 20, 120, new BigDecimal("0.000018"), "pricing-v1"));

        BedrockAgentShadowExecutor executor = new BedrockAgentShadowExecutor(
                clientProvider,
                new AiProperties("bedrock", "model-v1", "us-east-1", "pricing-v1", BigDecimal.ZERO, BigDecimal.ZERO));
        AgentShadowExecutionResult result = executor.execute(new AgentShadowExecutionRequest(
                definition(),
                Channel.TELEGRAM,
                "CATALOG_SEARCH",
                new com.wally.customersupport.catalog.domain.model.CatalogQuery(
                        "NullPointer", null, "M", "Negro"),
                "Encontré una remera fuente de datos."));

        assertThat(result.outcome()).isEqualTo("COMPLETED");
        assertThat(result.inputTokens()).isEqualTo(100);
        assertThat(result.outputTokens()).isEqualTo(20);
        assertThat(result.candidateOutputDigest()).isNotBlank();
    }

    @Test
    void failsClosedWhenBedrockClientIsUnavailable() {
        when(clientProvider.getIfAvailable()).thenReturn(null);
        BedrockAgentShadowExecutor executor = new BedrockAgentShadowExecutor(
                clientProvider,
                new AiProperties("bedrock", "model-v1", "us-east-1", "pricing-v1", BigDecimal.ZERO, BigDecimal.ZERO));

        AgentShadowExecutionResult result = executor.execute(new AgentShadowExecutionRequest(
                definition(), Channel.TELEGRAM, "CATALOG_SEARCH", null, null));

        assertThat(result.outcome()).isEqualTo("FAILED");
        assertThat(result.fallbackReason()).isEqualTo("BEDROCK_CLIENT_UNAVAILABLE");
        assertThat(result.candidateOutputDigest()).isNull();
    }

    private static AgentRuntimeDefinition definition() {
        return new AgentRuntimeDefinition(
                "catalog-specialist",
                2,
                "Catalog specialist",
                "Search products using deterministic catalog tools",
                "bedrock",
                "model-v1",
                AgentInferenceParameters.deterministic(),
                "system-v1",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "catalog-input-v1",
                "catalog-output-v1",
                Set.of("catalog.search"),
                Set.of(),
                "conversation-summary-v1",
                "grounded-customer-support-v1",
                Duration.ofSeconds(10),
                2,
                2_000,
                1_000,
                BigDecimal.valueOf(0.05),
                "safe-fallback",
                "catalog-eval-v1");
    }
}
