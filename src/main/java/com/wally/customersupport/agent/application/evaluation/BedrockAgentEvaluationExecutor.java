package com.wally.customersupport.agent.application.evaluation;

import java.util.List;
import java.util.Map;

import com.wally.customersupport.agent.application.service.AgentRuntimeDefinition;
import com.wally.customersupport.agent.domain.model.AgentEvaluationExecution;
import com.wally.customersupport.agent.domain.model.AgentEvaluationExecutionMetadata;
import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import com.wally.customersupport.agent.infrastructure.config.AgentEvaluationProperties;
import com.wally.customersupport.catalog.application.service.CatalogResponseFactsFormatter;
import com.wally.customersupport.conversation.application.port.out.MeasuredLlmClient;
import com.wally.customersupport.conversation.application.port.out.ResponseHumanizer;
import com.wally.customersupport.conversation.infrastructure.ai.bedrock.BedrockConversationIntentClassifier;
import com.wally.customersupport.conversation.infrastructure.ai.bedrock.ConversationIntentEvaluationExecution;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptTemplateRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Executes bounded Bedrock agent evaluations from immutable SQL versions on synthetic datasets. */
@Component
@ConditionalOnProperty(name = "wcs.agent-evaluation.executor", havingValue = "bedrock")
public class BedrockAgentEvaluationExecutor implements AgentEvaluationExecutor {

    private static final String RESPONSE_AGENT_ID = "response-humanization";
    private static final String ROUTER_AGENT_ID = "conversation-router";
    private static final String RESPONSE_DATASET_VERSION = "catalog-response-v1";
    private static final java.util.Set<String> ROUTER_DATASET_VERSIONS = java.util.Set.of(
            ConversationRouterEvaluationDatasetProvider.VERSION,
            ConversationRouterEvaluationDatasetProvider.VERSION_2);
    private static final String POLICY_ID = "bedrock-response-humanizer";

    private final MeasuredLlmClient measuredLlmClient;
    private final AgentEvaluationVersionResolver versionResolver;
    private final AgentEvaluationProperties evaluationProperties;
    private final ResponseHumanizer fallbackHumanizer;
    private final BedrockConversationIntentClassifier routerClassifier;

    @Autowired
    BedrockAgentEvaluationExecutor(
            MeasuredLlmClient measuredLlmClient,
            AgentEvaluationVersionResolver versionResolver,
            AgentEvaluationProperties evaluationProperties,
            ResponseHumanizer fallbackHumanizer,
            BedrockConversationIntentClassifier routerClassifier) {
        this.measuredLlmClient = measuredLlmClient;
        this.versionResolver = versionResolver;
        this.evaluationProperties = evaluationProperties;
        this.fallbackHumanizer = fallbackHumanizer;
        this.routerClassifier = routerClassifier;
    }

    BedrockAgentEvaluationExecutor(
            MeasuredLlmClient measuredLlmClient,
            AgentEvaluationVersionResolver versionResolver,
            AgentEvaluationProperties evaluationProperties,
            ResponseHumanizer fallbackHumanizer) {
        this(measuredLlmClient, versionResolver, evaluationProperties, fallbackHumanizer, null);
    }

    @Override
    public AgentEvaluationRunRequest prepare(AgentEvaluationRunRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("evaluation request must not be null");
        }
        if (!isSupportedAgent(request.agentId())) {
            throw new IllegalArgumentException("the selected evaluation dataset does not support this agent");
        }
        validateDatasetAgentPair(request);
        if (versionResolver == null) {
            throw new IllegalStateException("SQL-backed evaluation version resolver is unavailable");
        }
        return versionResolver.resolve(request);
    }

    @Override
    public void validate(AgentEvaluationRunRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("evaluation request must not be null");
        }
        if (!isSupportedAgent(request.agentId())) {
            throw new IllegalArgumentException("the selected evaluation dataset does not support this agent");
        }
        validateDatasetAgentPair(request);
        if (request.versionDefinition() == null) {
            throw new IllegalArgumentException("evaluation requires an immutable SQL agent version");
        }
        if (!request.datasetVersion().equals(request.versionDefinition().evaluationSuiteVersion())) {
            throw new IllegalArgumentException("evaluation dataset must match the SQL version evaluation suite");
        }
        if (!"bedrock".equalsIgnoreCase(request.provider())
                || !request.versionDefinition().modelProvider().equalsIgnoreCase(request.provider())
                || !request.versionDefinition().modelId().equals(request.modelId())) {
            throw new IllegalArgumentException("evaluation execution metadata must match the selected SQL version");
        }
    }

    @Override
    public void validateScenarios(
            List<AgentEvaluationScenario> scenarios,
            AgentEvaluationRunRequest request) {
        validate(request);
        AgentRuntimeDefinition profile = AgentRuntimeDefinition.forEvaluation(request.versionDefinition());
        if (RESPONSE_AGENT_ID.equals(request.agentId())) {
            scenarios.stream()
                    .filter(scenario -> scenario.request() != null)
                    .forEach(scenario -> buildUserPrompt(scenario, profile));
        } else if (scenarios.stream().anyMatch(scenario -> scenario.routingContext() == null)) {
            throw new IllegalArgumentException("router evaluation dataset contains a non-routing scenario");
        }
    }

    @Override
    public AgentEvaluationExecution execute(AgentEvaluationScenario scenario) {
        throw new IllegalStateException("Bedrock evaluation requires a resolved SQL version");
    }

    @Override
    public AgentEvaluationExecution execute(
            AgentEvaluationScenario scenario,
            AgentEvaluationRunRequest request) {
        validate(request);
        if (scenario == null) {
            throw new IllegalArgumentException("evaluation scenario must not be null");
        }
        if (ROUTER_AGENT_ID.equals(request.agentId())) {
            return executeRouterScenario(scenario, request);
        }
        if (scenario.request() == null) {
            return new AgentEvaluationExecution(fallbackHumanizer.humanize(null), null);
        }

        AgentRuntimeDefinition profile = AgentRuntimeDefinition.forEvaluation(request.versionDefinition());
        var completion = measuredLlmClient.completeMeasuredForAgent(
                "agent-evaluation",
                "agent.evaluation.response.generate",
                buildUserPrompt(scenario, profile),
                profile,
                evaluationProperties.effectiveTimeout(),
                null);
        return new AgentEvaluationExecution(
                ResponseHumanizationResult.applied(completion.text(), POLICY_ID, profile.semanticVersion()),
                new AgentEvaluationExecutionMetadata(
                        profile.agentId(),
                        Integer.toString(profile.agentVersion()),
                        completion.provider(),
                        completion.modelId(),
                        completion.durationMs(),
                        completion.providerLatencyMs(),
                        completion.inputTokens(),
                        completion.outputTokens(),
                        completion.totalTokens(),
                        completion.estimatedCostUsd(),
                        completion.pricingVersion()));
    }

    private AgentEvaluationExecution executeRouterScenario(
            AgentEvaluationScenario scenario,
            AgentEvaluationRunRequest request) {
        if (scenario.routingContext() == null || routerClassifier == null) {
            throw new IllegalArgumentException("router evaluation requires a routing scenario and Bedrock classifier");
        }
        AgentRuntimeDefinition profile = AgentRuntimeDefinition.forEvaluation(request.versionDefinition());
        ConversationIntentEvaluationExecution result = routerClassifier.classifyForEvaluation(
                scenario.routingContext(), profile, evaluationProperties.effectiveTimeout());
        var completion = result.completion();
        var decision = result.decision();
        return new AgentEvaluationExecution(
                null,
                new AgentEvaluationExecutionMetadata(
                        profile.agentId(),
                        Integer.toString(profile.agentVersion()),
                        completion.provider(),
                        completion.modelId(),
                        completion.durationMs(),
                        completion.providerLatencyMs(),
                        completion.inputTokens(),
                        completion.outputTokens(),
                        completion.totalTokens(),
                        completion.estimatedCostUsd(),
                        completion.pricingVersion(),
                        decision.intent().name(),
                        decision.action().name(),
                        queryAttributes(decision.catalogQuery()),
                        null,
                        null,
                        null,
                        decision.quantity()),
                decision);
    }

    private String buildUserPrompt(AgentEvaluationScenario scenario, AgentRuntimeDefinition profile) {
        int inputCharacterLimit = Math.min(12_000, Math.multiplyExact(profile.maxInputTokens(), 4));
        var result = scenario.request().catalogResult();
        String userPrompt = PromptTemplateRenderer.render(profile.invocationConfiguration().userPromptTemplate(), Map.of(
                "use_case", scenario.useCase(),
                "channel", scenario.channel().name(),
                "approved_knowledge", limit(CatalogResponseFactsFormatter.approvedKnowledge(result), inputCharacterLimit),
                "required_facts", limit(CatalogResponseFactsFormatter.requiredFacts(result), inputCharacterLimit)));
        int estimatedInputTokens = (profile.invocationConfiguration().systemPrompt().length()
                + userPrompt.length() + 3) / 4;
        int inputTokenLimit = Math.min(
                profile.maxInputTokens(),
                evaluationProperties.effectiveMaxInputTokensPerScenario());
        if (estimatedInputTokens > inputTokenLimit) {
            throw new IllegalArgumentException("evaluation input exceeds the configured token limit");
        }
        return userPrompt;
    }

    private static String limit(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    private static boolean isSupportedAgent(String agentId) {
        return RESPONSE_AGENT_ID.equals(agentId) || ROUTER_AGENT_ID.equals(agentId);
    }

    private static void validateDatasetAgentPair(AgentEvaluationRunRequest request) {
        boolean supportedDataset = ROUTER_AGENT_ID.equals(request.agentId())
                ? ROUTER_DATASET_VERSIONS.contains(request.datasetVersion())
                : RESPONSE_DATASET_VERSION.equals(request.datasetVersion());
        if (!supportedDataset) {
            throw new IllegalArgumentException("the selected dataset version is not compatible with this agent");
        }
    }

    private static List<String> queryAttributes(com.wally.customersupport.catalog.domain.model.CatalogQuery query) {
        if (query == null) return List.of();
        List<String> values = new java.util.ArrayList<>();
        addQueryAttribute(values, "name", query.name());
        addQueryAttribute(values, "sku", query.sku());
        addQueryAttribute(values, "productType", query.productType());
        addQueryAttribute(values, "color", query.color());
        addQueryAttribute(values, "size", query.size());
        addQueryAttribute(values, "minPrice", query.minPrice() == null
                ? null : query.minPrice().stripTrailingZeros().toPlainString());
        addQueryAttribute(values, "maxPrice", query.maxPrice() == null
                ? null : query.maxPrice().stripTrailingZeros().toPlainString());
        return values.stream().sorted().toList();
    }

    private static void addQueryAttribute(List<String> target, String name, String value) {
        if (value != null) target.add(name + "=" + value);
    }
}
