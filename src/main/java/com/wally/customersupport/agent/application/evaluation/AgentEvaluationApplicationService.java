package com.wally.customersupport.agent.application.evaluation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.wally.customersupport.agent.application.port.out.AgentEvaluationRunRepository;
import com.wally.customersupport.agent.domain.model.AgentEvaluationQualityScorecard;
import com.wally.customersupport.agent.domain.model.AgentEvaluationSuiteResult;
import com.wally.customersupport.agent.infrastructure.config.AgentEvaluationProperties;
import com.wally.customersupport.shared.infrastructure.observability.AiPricingCalculator;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Application boundary for sanitized, offline evaluation runs. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentEvaluationApplicationService {

    private final AgentEvaluationDatasetCatalog datasetCatalog;
    private final AgentEvaluationRunner runner;
    private final AgentEvaluationRunRepository runRepository;
    private final Clock clock;
    private final AgentEvaluationProperties properties;

    public AgentEvaluationRun execute(
            AgentEvaluationRunRequest request,
            AgentEvaluationExecutor executor) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(executor, "executor");

        UUID runId = UUID.randomUUID();
        Instant startedAt = clock.instant();
        try {
            AgentEvaluationRunRequest resolvedRequest = Objects.requireNonNull(
                    executor.prepare(request), "executor.prepare must not return null");
            executor.validate(resolvedRequest);
            var scenarios = datasetCatalog.scenarios(resolvedRequest.datasetVersion());
            validateLimits(resolvedRequest, scenarios.size());
            executor.validateScenarios(scenarios, resolvedRequest);
            AgentEvaluationSuiteResult suiteResult = runner.run(
                    scenarios,
                    resolvedRequest,
                    executor);
            Instant completedAt = clock.instant();
            AgentEvaluationRun run = new AgentEvaluationRun(
                    runId,
                    resolvedRequest.datasetVersion(),
                    resolvedRequest.agentId(),
                    resolvedRequest.agentVersion(),
                    resolvedRequest.provider(),
                    resolvedRequest.modelId(),
                    startedAt,
                    completedAt,
                    elapsedMillis(startedAt, completedAt),
                    suiteResult);
            AgentEvaluationRun savedRun = Objects.requireNonNull(
                    runRepository.save(run),
                    "runRepository.save must not return null");
            logCompleted(savedRun);
            return savedRun;
        } catch (RuntimeException exception) {
            logFailed(runId, request, startedAt, exception);
            throw exception;
        }
    }

    private void logCompleted(AgentEvaluationRun run) {
        AgentEvaluationSuiteResult result = run.suiteResult();
        AgentEvaluationQualityScorecard scorecard = result.qualityScorecard();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("runId", run.runId().toString());
        fields.put("datasetVersion", run.datasetVersion());
        fields.put("agentId", run.agentId());
        fields.put("agentVersion", run.agentVersion());
        fields.put("provider", run.provider());
        fields.put("model", run.modelId());
        fields.put("totalScenarios", result.totalScenarios());
        fields.put("passedScenarios", result.passedScenarios());
        fields.put("failedScenarios", result.failedScenarios());
        fields.put("passRate", result.passRate());
        fields.put("averageScore", result.averageScore());
        fields.put("durationMs", run.durationMs());
        StructuredEventLog.info(log, "AGENT_EVALUATION_COMPLETED", fields);

        Map<String, Object> scorecardFields = new LinkedHashMap<>();
        scorecardFields.put("runId", run.runId().toString());
        scorecardFields.put("datasetVersion", run.datasetVersion());
        scorecardFields.put("agentId", run.agentId());
        scorecardFields.put("agentVersion", run.agentVersion());
        scorecardFields.put("provider", run.provider());
        scorecardFields.put("model", run.modelId());
        scorecardFields.put("evaluatedScenarios", scorecard.evaluatedScenarios());
        scorecardFields.put("responseValidityRate", scorecard.responseValidityRate());
        scorecardFields.put("responseGroundingRate", scorecard.responseGroundingRate());
        scorecardFields.put("safetyRate", scorecard.safetyRate());
        scorecardFields.put("utilityRate", scorecard.utilityRate());
        scorecardFields.put("intentAccuracyRate", scorecard.intentAccuracyRate());
        scorecardFields.put("entityExtractionRate", scorecard.entityExtractionRate());
        scorecardFields.put("toolSuccessRate", scorecard.toolSuccessRate());
        scorecardFields.put("ragGroundingRate", scorecard.ragGroundingRate());
        scorecardFields.put("failureCounts", scorecard.failureCounts());
        scorecardFields.put("unavailableDimensions", scorecard.unavailableDimensions());
        scorecardFields.put("durationMs", run.durationMs());
        StructuredEventLog.info(log, "AGENT_EVALUATION_SCORECARD", scorecardFields);
    }

    private void logFailed(
            UUID runId,
            AgentEvaluationRunRequest request,
            Instant startedAt,
            RuntimeException exception) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("runId", runId.toString());
        fields.put("datasetVersion", request.datasetVersion());
        fields.put("agentId", request.agentId());
        fields.put("agentVersion", request.agentVersion());
        fields.put("provider", request.provider());
        fields.put("model", request.modelId());
        fields.put("durationMs", elapsedMillis(startedAt, clock.instant()));
        fields.put("errorType", exception.getClass().getSimpleName());
        StructuredEventLog.warn(log, "AGENT_EVALUATION_FAILED", fields);
    }

    private static long elapsedMillis(Instant startedAt, Instant completedAt) {
        return Math.max(0, Duration.between(startedAt, completedAt).toMillis());
    }

    private void validateLimits(AgentEvaluationRunRequest request, int scenarioCount) {
        if (scenarioCount > properties.effectiveMaxScenarios()) {
            throw new IllegalArgumentException("evaluation scenario limit exceeded");
        }
        if ("bedrock".equalsIgnoreCase(request.provider())) {
            var version = Objects.requireNonNull(
                    request.versionDefinition(), "Bedrock evaluation requires an immutable SQL version");
            if (version.maxOutputTokens() > properties.effectiveMaxOutputTokens()) {
                throw new IllegalArgumentException("agent output limit exceeds evaluator safety cap");
            }
            int inputTokens = Math.min(
                    properties.effectiveMaxInputTokensPerScenario(), version.maxInputTokens()) * scenarioCount;
            int outputTokens = version.maxOutputTokens() * scenarioCount;
            var pricing = version.invocationConfiguration();
            var estimatedCost = AiPricingCalculator.estimatedCostUsd(
                    inputTokens,
                    outputTokens,
                    pricing.inputPriceUsdPerMillionTokens(),
                    pricing.outputPriceUsdPerMillionTokens());
            if (estimatedCost.compareTo(properties.effectiveMaxEstimatedCostUsd()) > 0) {
                throw new IllegalArgumentException("evaluation estimated budget exceeded");
            }
        }
    }
}
