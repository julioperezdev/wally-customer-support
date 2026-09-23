package com.wally.customersupport.agent.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import com.wally.customersupport.conversation.infrastructure.ai.bedrock.BedrockConversationIntentClassifier;
import com.wally.customersupport.conversation.infrastructure.ai.bedrock.ConversationIntentEvaluationExecution;
import org.junit.jupiter.api.Test;

class BedrockAgentEvaluationExecutorTest {

    private final MeasuredLlmClient client = mock(MeasuredLlmClient.class);
    private final AgentEvaluationVersionResolver versionResolver = mock(AgentEvaluationVersionResolver.class);
    private final ResponseHumanizer fallback = mock(ResponseHumanizer.class);
    private final BedrockConversationIntentClassifier routerClassifier = mock(BedrockConversationIntentClassifier.class);
    private final BedrockAgentEvaluationExecutor executor = new BedrockAgentEvaluationExecutor(
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

    @Test
    void executesTheSelectedRouterPromptAndReturnsOnlyDecisionAndMeasuredMetadata() {
        AgentVersion version = routerVersion();
        when(versionResolver.resolve(any(AgentEvaluationRunRequest.class))).thenAnswer(invocation ->
                invocation.<AgentEvaluationRunRequest>getArgument(0).withVersionDefinition(version));
        ConversationContext context = new ConversationContext(
                null, null, "Quiero una remera negra talle M",
                java.util.List.of("Quiero una remera negra talle M"), java.util.List.of(),
                null, java.util.List.of(), Channel.TELEGRAM);
        AgentEvaluationScenario scenario = new AgentEvaluationScenario(
                "router_catalog_search", "conversation-routing-v1", "ROUTING", Channel.TELEGRAM,
                null, ResponseHumanizationResult.Outcome.APPLIED, java.util.List.of(), java.util.List.of(),
                "CATALOG_SEARCH", java.util.List.of("color=negro", "productType=remera", "size=M"),
                null, null, context, "CATALOG_SEARCH");
        ConversationIntentDecision decision = new ConversationIntentDecision(
                ConversationIntent.CATALOG_SEARCH, ConversationAction.CATALOG_SEARCH, 0.95,
                new com.wally.customersupport.catalog.domain.model.CatalogQuery(
                        null, null, "M", "negro", "remera", null, null), null, 1, java.util.List.of());
        when(routerClassifier.classifyForEvaluation(
                any(ConversationContext.class), any(AgentRuntimeDefinition.class), eq(Duration.ofSeconds(30))))
                .thenReturn(new ConversationIntentEvaluationExecution(decision,
                        new MeasuredLlmClient.LlmCompletion("", "bedrock", "model-v2", 25, 22L,
                                280, 70, 350, new BigDecimal("0.000042"), "pricing-v2")));
        BedrockAgentEvaluationExecutor routerExecutor = new BedrockAgentEvaluationExecutor(
                client, versionResolver,
                new AgentEvaluationProperties("bedrock", 40, 4_000, 1_024,
                        new BigDecimal("0.5"), Duration.ofSeconds(30)),
                fallback, routerClassifier);

        AgentEvaluationRunRequest prepared = routerExecutor.prepare(new AgentEvaluationRunRequest(
                "conversation-routing-v1", "conversation-router", "2"));
        routerExecutor.validateScenarios(java.util.List.of(scenario), prepared);
        AgentEvaluationExecution result = routerExecutor.execute(scenario, prepared);

        assertThat(result.response()).isNull();
        assertThat(result.routingDecision()).isEqualTo(decision);
        assertThat(result.metadata().agentId()).isEqualTo("conversation-router");
        assertThat(result.metadata().routedIntent()).isEqualTo("CATALOG_SEARCH");
        assertThat(result.metadata().routedAction()).isEqualTo("CATALOG_SEARCH");
        assertThat(result.metadata().routedQuantity()).isEqualTo(1);
        assertThat(result.metadata().resolvedEntityTypes()).containsExactly(
                "color=negro", "productType=remera", "size=M");
        assertThat(result.metadata().totalTokens()).isEqualTo(350);
        assertThat(result.metadata().estimatedCostUsd()).isEqualByComparingTo("0.000042");
        verify(routerClassifier).classifyForEvaluation(
                any(ConversationContext.class), any(AgentRuntimeDefinition.class), eq(Duration.ofSeconds(30)));
        verifyNoInteractions(client);
    }

    @Test
    void acceptsV2OnlyWhenTheImmutableRouterVersionDeclaresThatSuite() {
        when(versionResolver.resolve(any(AgentEvaluationRunRequest.class))).thenAnswer(invocation ->
                invocation.<AgentEvaluationRunRequest>getArgument(0)
                        .withVersionDefinition(routerVersion("conversation-routing-v2")));
        BedrockAgentEvaluationExecutor routerExecutor = new BedrockAgentEvaluationExecutor(
                client, versionResolver,
                new AgentEvaluationProperties("bedrock", 40, 4_000, 1_024,
                        new BigDecimal("0.5"), Duration.ofSeconds(30)),
                fallback, routerClassifier);

        AgentEvaluationRunRequest prepared = routerExecutor.prepare(new AgentEvaluationRunRequest(
                "conversation-routing-v2", "conversation-router", "2"));

        routerExecutor.validate(prepared);
        assertThat(prepared.versionDefinition().evaluationSuiteVersion()).isEqualTo("conversation-routing-v2");
    }

    @Test
    void rejectsDatasetVersionThatDoesNotBelongToSelectedAgentBeforeResolvingPrompt() {
        AgentEvaluationRunRequest request = new AgentEvaluationRunRequest(
                "catalog-response-v1", "conversation-router", "2");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> executor.prepare(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("compatible with this agent");
        verifyNoInteractions(versionResolver);
    }

    @Test
    void rejectsDatasetThatDoesNotMatchTheImmutableSqlVersionSuite() {
        when(versionResolver.resolve(any(AgentEvaluationRunRequest.class))).thenAnswer(invocation ->
                invocation.<AgentEvaluationRunRequest>getArgument(0)
                        .withVersionDefinition(routerVersion("different-routing-suite")));
        BedrockAgentEvaluationExecutor routerExecutor = new BedrockAgentEvaluationExecutor(
                client, versionResolver,
                new AgentEvaluationProperties("bedrock", 40, 4_000, 1_024,
                        new BigDecimal("0.5"), Duration.ofSeconds(30)),
                fallback, routerClassifier);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> routerExecutor.validate(routerExecutor.prepare(
                new AgentEvaluationRunRequest("conversation-routing-v1", "conversation-router", "2"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must match the SQL version evaluation suite");
        verifyNoInteractions(client, routerClassifier);
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

    private static AgentVersion routerVersion() {
        return routerVersion("conversation-routing-v1");
    }

    private static AgentVersion routerVersion(String evaluationSuiteVersion) {
        String systemPrompt = "Route customer requests to the best WCS action.";
        AgentInvocationConfiguration invocation = new AgentInvocationConfiguration(
                systemPrompt,
                "<history>{{conversation_history}}</history><message>{{latest_customer_message}}</message>"
                        + "{{conversation_summary_section}}{{customer_preferences_section}}{{active_selection_section}}",
                "{\"type\":\"object\"}",
                "{\"type\":\"object\"}",
                "medium",
                false,
                "pricing-v2",
                new BigDecimal("0.1"),
                new BigDecimal("0.2"));
        AgentVersion draft = AgentVersion.draft(
                "conversation-router",
                2,
                "1.1.0",
                "Conversation router candidate",
                "Maps customer messages to a bounded action and structured filters",
                "bedrock",
                "model-v2",
                new AgentInferenceParameters(new BigDecimal("0.0"), new BigDecimal("0.9")),
                "conversation-router-v2",
                AgentInvocationConfiguration.sha256(systemPrompt.trim()),
                "conversation-route-input-v2",
                "conversation-route-output-v1",
                Set.of(),
                Set.of(),
                "conversation-summary-v1",
                "validated-route-v1",
                Duration.ofSeconds(30),
                1,
                500,
                1_024,
                new BigDecimal("0.05"),
                null,
                evaluationSuiteVersion,
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
