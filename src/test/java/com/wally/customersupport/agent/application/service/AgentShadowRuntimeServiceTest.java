package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.wally.customersupport.agent.application.port.out.AgentShadowExecutor;
import com.wally.customersupport.agent.application.port.out.AgentTrafficComparisonEventPublisher;
import com.wally.customersupport.agent.application.shadow.AgentShadowExecutionRequest;
import com.wally.customersupport.agent.application.shadow.AgentShadowExecutionResult;
import com.wally.customersupport.agent.application.shadow.AgentTrafficComparisonEvent;
import com.wally.customersupport.agent.application.shadow.ShadowResponseDigest;
import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.shared.infrastructure.config.AgentRuntimeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentShadowRuntimeServiceTest {

    @Mock
    private AgentShadowExecutor executor;
    @Mock
    private AgentTrafficComparisonEventPublisher eventPublisher;

    private ConversationContext context;
    private AgentRuntimeDefinition definition;

    @BeforeEach
    void setUp() {
        context = new ConversationContext(
                UUID.randomUUID(), "customer-1", "¿Qué productos tienen?", List.of(), List.of(),
                null, List.of(), Channel.TELEGRAM);
        definition = definition();
    }

    @Test
    void remainsClosedByDefault() {
        AgentShadowRuntimeService service = service(new AgentRuntimeProperties(
                false, "prod", false, Duration.ofSeconds(5), "noop"));

        AgentShadowExecutionResult result = service.executeIfEnabled(
                AgentRuntimeDefinitionResolution.active(definition), context, "CATALOG_SEARCH");

        assertThat(result.outcome()).isEqualTo("DISABLED");
        verify(executor, never()).execute(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void executesCandidateAndPublishesOnlySanitizedEvidence() {
        AgentShadowRuntimeService service = service(new AgentRuntimeProperties(
                true, "prod", true, Duration.ofSeconds(1), "noop"));
        when(executor.execute(any(AgentShadowExecutionRequest.class)))
                .thenReturn(new AgentShadowExecutionResult(
                        "COMPLETED", 12, 40, 20, 60, new BigDecimal("0.001"), null));

        AgentShadowExecutionResult result = service.executeIfEnabled(
                AgentRuntimeDefinitionResolution.active(definition), context, "CATALOG_SEARCH");

        assertThat(result.outcome()).isEqualTo("COMPLETED");
        ArgumentCaptor<AgentTrafficComparisonEvent> eventCaptor =
                ArgumentCaptor.forClass(AgentTrafficComparisonEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        AgentTrafficComparisonEvent event = eventCaptor.getValue();
        assertThat(event.mode()).isEqualTo(com.wally.customersupport.agent.application.shadow.AgentTrafficMode.SHADOW);
        assertThat(event.candidateResponsePublished()).isFalse();
        assertThat(event.pseudonymizedConversationId()).doesNotContain(context.conversationId().toString());
        assertThat(event.modelId()).isEqualTo("openai.gpt-oss-20b-1:0");
    }

    @Test
    void convertsBudgetOverflowIntoARejectedCandidate() {
        AgentShadowRuntimeService service = service(new AgentRuntimeProperties(
                true, "prod", true, Duration.ofSeconds(1), "noop"));
        when(executor.execute(any(AgentShadowExecutionRequest.class)))
                .thenReturn(new AgentShadowExecutionResult(
                        "COMPLETED", 12, 40, 20, 60, new BigDecimal("0.06"), null));

        AgentShadowExecutionResult result = service.executeIfEnabled(
                AgentRuntimeDefinitionResolution.active(definition), context, "CATALOG_SEARCH");

        assertThat(result.outcome()).isEqualTo("LIMIT_EXCEEDED");
        assertThat(result.fallbackReason()).isEqualTo("SHADOW_BUDGET_LIMIT");
    }

    @Test
    void convertsExecutorFailureIntoEvidenceWithoutThrowing() {
        AgentShadowRuntimeService service = service(new AgentRuntimeProperties(
                true, "prod", true, Duration.ofSeconds(1), "noop"));
        when(executor.execute(any(AgentShadowExecutionRequest.class)))
                .thenThrow(new IllegalStateException("provider unavailable"));

        AgentShadowExecutionResult result = service.executeIfEnabled(
                AgentRuntimeDefinitionResolution.active(definition), context, "CATALOG_SEARCH");

        assertThat(result.outcome()).isEqualTo("FAILED");
        assertThat(result.fallbackReason()).isEqualTo("SHADOW_EXECUTOR_FAILED");
        verify(eventPublisher).publish(any(AgentTrafficComparisonEvent.class));
    }

    @Test
    void comparesCandidateDigestWithoutPersistingCandidateText() {
        AgentShadowRuntimeService service = service(new AgentRuntimeProperties(
                true, "prod", true, Duration.ofSeconds(1), "noop"));
        String activeResponse = "Encontré una remera fuente de datos.";
        when(executor.execute(any(AgentShadowExecutionRequest.class)))
                .thenReturn(new AgentShadowExecutionResult(
                        "COMPLETED",
                        12,
                        40,
                        20,
                        60,
                        new BigDecimal("0.001"),
                        null,
                        ShadowResponseDigest.sha256(activeResponse)));

        AgentShadowExecutionResult result = service.executeIfEnabled(
                AgentRuntimeDefinitionResolution.active(definition()),
                context,
                "CATALOG_SEARCH",
                new CatalogQuery("NullPointer", null, "M", "Negro"),
                activeResponse);

        assertThat(result.candidateOutputDigest()).isNotBlank();
        ArgumentCaptor<AgentTrafficComparisonEvent> eventCaptor =
                ArgumentCaptor.forClass(AgentTrafficComparisonEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().comparisonOutcome()).isEqualTo("MATCH");
        assertThat(eventCaptor.getValue().toString()).doesNotContain(activeResponse);

        ArgumentCaptor<AgentShadowExecutionRequest> requestCaptor =
                ArgumentCaptor.forClass(AgentShadowExecutionRequest.class);
        verify(executor).execute(requestCaptor.capture());
        assertThat(requestCaptor.getValue().channel()).isEqualTo(Channel.TELEGRAM);
        assertThat(requestCaptor.getValue().catalogQuery().name()).isEqualTo("NullPointer");
        assertThat(requestCaptor.getValue().sanitizedReferenceResponse()).isEqualTo(activeResponse);
    }

    @Test
    void classifiesDifferentCandidateDigestAsMismatch() {
        AgentShadowRuntimeService service = service(new AgentRuntimeProperties(
                true, "prod", true, Duration.ofSeconds(1), "noop"));
        String activeResponse = "Respuesta activa";
        when(executor.execute(any(AgentShadowExecutionRequest.class)))
                .thenReturn(new AgentShadowExecutionResult(
                        "COMPLETED", 12, 40, 20, 60, new BigDecimal("0.001"), null,
                        ShadowResponseDigest.sha256("Respuesta candidata")));

        service.executeIfEnabled(
                AgentRuntimeDefinitionResolution.active(definition()),
                context,
                "CATALOG_SEARCH",
                null,
                activeResponse);

        ArgumentCaptor<AgentTrafficComparisonEvent> eventCaptor =
                ArgumentCaptor.forClass(AgentTrafficComparisonEvent.class);
        verify(eventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().comparisonOutcome()).isEqualTo("MISMATCH");
    }

    private AgentShadowRuntimeService service(AgentRuntimeProperties properties) {
        return new AgentShadowRuntimeService(
                executor,
                eventPublisher,
                new com.wally.customersupport.agent.application.shadow.AgentTrafficRoutingPolicy(),
                properties);
    }

    private static AgentRuntimeDefinition definition() {
        return new AgentRuntimeDefinition(
                "catalog-specialist",
                2,
                "Catalog specialist",
                "Search products using deterministic catalog tools",
                "bedrock",
                "openai.gpt-oss-20b-1:0",
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
