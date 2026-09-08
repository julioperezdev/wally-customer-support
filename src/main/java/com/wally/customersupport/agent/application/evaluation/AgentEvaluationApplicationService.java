package com.wally.customersupport.agent.application.evaluation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.wally.customersupport.agent.application.port.out.AgentEvaluationRunRepository;
import com.wally.customersupport.agent.domain.model.AgentEvaluationSuiteResult;
import com.wally.customersupport.agent.infrastructure.config.AgentEvaluationProperties;
import com.wally.customersupport.shared.infrastructure.config.AiProperties;
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
    private final AiProperties aiProperties;

    public AgentEvaluationRun execute(
            AgentEvaluationRunRequest request,
            AgentEvaluationExecutor executor) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(executor, "executor");

        UUID runId = UUID.randomUUID();
        Instant startedAt = clock.instant();
        try {
            executor.validate(request);
            var scenarios = datasetCatalog.scenarios(request.datasetVersion());
            validateLimits(request, scenarios.size());
            AgentEvaluationSuiteResult suiteResult = runner.run(
                    scenarios,
                    request,
                    executor);
            Instant completedAt = clock.instant();
            AgentEvaluationRun run = new AgentEvaluationRun(
                    runId,
                    request.datasetVersion(),
                    request.agentId(),
                    request.agentVersion(),
                    request.provider(),
                    request.modelId(),
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
            int inputTokens = properties.effectiveMaxInputTokensPerScenario() * scenarioCount;
            int outputTokens = properties.effectiveMaxOutputTokens() * scenarioCount;
            var estimatedCost = AiPricingCalculator.estimatedCostUsd(
                    inputTokens,
                    outputTokens,
                    aiProperties.effectiveInputPriceUsdPerMillionTokens(),
                    aiProperties.effectiveOutputPriceUsdPerMillionTokens());
            if (estimatedCost.compareTo(properties.effectiveMaxEstimatedCostUsd()) > 0) {
                throw new IllegalArgumentException("evaluation estimated budget exceeded");
            }
        }
    }
}
