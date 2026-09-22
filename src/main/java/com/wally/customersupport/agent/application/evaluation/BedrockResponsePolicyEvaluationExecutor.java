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
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import com.wally.customersupport.conversation.infrastructure.ai.prompt.PromptTemplateRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Evaluates one immutable SQL-backed response-humanization version on synthetic catalog cases. */
@Component
@ConditionalOnProperty(name = "wcs.agent-evaluation.executor", havingValue = "bedrock")
public class BedrockResponsePolicyEvaluationExecutor implements AgentEvaluationExecutor {

    private static final String SUPPORTED_AGENT_ID = "response-humanization";
    private static final String POLICY_ID = "bedrock-response-humanizer";

    private final MeasuredLlmClient measuredLlmClient;
    private final AgentEvaluationVersionResolver versionResolver;
    private final AgentEvaluationProperties evaluationProperties;
    private final ResponseHumanizer fallbackHumanizer;

    @Autowired
    BedrockResponsePolicyEvaluationExecutor(
            MeasuredLlmClient measuredLlmClient,
            AgentEvaluationVersionResolver versionResolver,
            AgentEvaluationProperties evaluationProperties,
            ResponseHumanizer fallbackHumanizer) {
        this.measuredLlmClient = measuredLlmClient;
        this.versionResolver = versionResolver;
        this.evaluationProperties = evaluationProperties;
        this.fallbackHumanizer = fallbackHumanizer;
    }

    @Override
    public AgentEvaluationRunRequest prepare(AgentEvaluationRunRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("evaluation request must not be null");
        }
        if (!SUPPORTED_AGENT_ID.equals(request.agentId())) {
            throw new IllegalArgumentException("this evaluation dataset supports response-humanization only");
        }
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
        if (!SUPPORTED_AGENT_ID.equals(request.agentId())) {
            throw new IllegalArgumentException("this evaluation dataset supports response-humanization only");
        }
        if (request.versionDefinition() == null) {
            throw new IllegalArgumentException("evaluation requires an immutable SQL agent version");
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
        scenarios.stream()
                .filter(scenario -> scenario.request() != null)
                .forEach(scenario -> buildUserPrompt(scenario, profile));
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
}
