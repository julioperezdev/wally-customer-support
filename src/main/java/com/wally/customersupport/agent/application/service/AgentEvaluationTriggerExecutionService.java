package com.wally.customersupport.agent.application.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationApplicationService;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationExecutor;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRun;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerAuthorizationDecision;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerExecutionReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerExecutionRequest;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerExecutionResult;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerExecutionStatus;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationTriggerExecutionGuard;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provider-neutral application boundary for an authenticated evaluation trigger.
 *
 * <p>Authorization is evaluated before the idempotency guard and the evaluation
 * executor. The guard must be backed by an atomic adapter before this boundary
 * is exposed to a remote trigger.</p>
 */
@RequiredArgsConstructor
@Slf4j
public class AgentEvaluationTriggerExecutionService {

    private final AgentEvaluationTriggerAuthorizationService authorizationService;
    private final AgentEvaluationApplicationService evaluationService;
    private final AgentEvaluationTriggerExecutionGuard executionGuard;

    public AgentEvaluationTriggerExecutionResult execute(
            AgentEvaluationTriggerExecutionRequest request,
            AgentEvaluationExecutor executor) {
        AgentEvaluationTriggerExecutionRequest triggerRequest = Objects.requireNonNull(request, "request");
        AgentEvaluationExecutor evaluationExecutor = Objects.requireNonNull(executor, "executor");

        AgentEvaluationTriggerAuthorizationDecision authorization = authorizationService.authorize(
                triggerRequest.authorizationRequest());
        if (!authorization.authorized()) {
            AgentEvaluationTriggerExecutionResult result = new AgentEvaluationTriggerExecutionResult(
                    AgentEvaluationTriggerExecutionStatus.DENIED,
                    AgentEvaluationTriggerExecutionReason.AUTHORIZATION_DENIED,
                    authorization.reason(),
                    null);
            logOutcome("AGENT_EVALUATION_TRIGGER_DENIED", triggerRequest, result, null);
            return result;
        }

        boolean acquired;
        try {
            acquired = executionGuard.tryAcquire(triggerRequest.authorizationRequest().idempotencyKey());
        } catch (RuntimeException exception) {
            AgentEvaluationTriggerExecutionResult result = failed(
                    AgentEvaluationTriggerExecutionReason.IDEMPOTENCY_GUARD_FAILED);
            logFailure("AGENT_EVALUATION_TRIGGER_FAILED", triggerRequest, result, exception);
            return result;
        }
        if (!acquired) {
            AgentEvaluationTriggerExecutionResult result = failed(
                    AgentEvaluationTriggerExecutionReason.IDEMPOTENCY_ALREADY_CLAIMED);
            logOutcome("AGENT_EVALUATION_TRIGGER_DUPLICATE", triggerRequest, result, null);
            return result;
        }

        try {
            AgentEvaluationRun run = evaluationService.execute(
                    triggerRequest.evaluationRequest(), evaluationExecutor);
            AgentEvaluationTriggerExecutionResult result = new AgentEvaluationTriggerExecutionResult(
                    AgentEvaluationTriggerExecutionStatus.COMPLETED,
                    AgentEvaluationTriggerExecutionReason.EVALUATION_COMPLETED,
                    null,
                    run.runId());
            logOutcome("AGENT_EVALUATION_TRIGGER_COMPLETED", triggerRequest, result, run.runId().toString());
            return result;
        } catch (RuntimeException exception) {
            AgentEvaluationTriggerExecutionResult result = failed(
                    AgentEvaluationTriggerExecutionReason.EVALUATION_FAILED);
            logFailure("AGENT_EVALUATION_TRIGGER_FAILED", triggerRequest, result, exception);
            return result;
        }
    }

    private static AgentEvaluationTriggerExecutionResult failed(
            AgentEvaluationTriggerExecutionReason reason) {
        return new AgentEvaluationTriggerExecutionResult(
                reason == AgentEvaluationTriggerExecutionReason.IDEMPOTENCY_ALREADY_CLAIMED
                        ? AgentEvaluationTriggerExecutionStatus.ALREADY_PROCESSED
                        : AgentEvaluationTriggerExecutionStatus.FAILED,
                reason,
                null,
                null);
    }

    private void logOutcome(
            String event,
            AgentEvaluationTriggerExecutionRequest request,
            AgentEvaluationTriggerExecutionResult result,
            String runId) {
        Map<String, Object> fields = baseLogFields(request, result);
        if (runId != null) {
            fields.put("runId", runId);
        }
        StructuredEventLog.info(log, event, fields);
    }

    private void logFailure(
            String event,
            AgentEvaluationTriggerExecutionRequest request,
            AgentEvaluationTriggerExecutionResult result,
            RuntimeException exception) {
        Map<String, Object> fields = baseLogFields(request, result);
        fields.put("errorType", exception.getClass().getSimpleName());
        StructuredEventLog.warn(log, event, fields);
    }

    private static Map<String, Object> baseLogFields(
            AgentEvaluationTriggerExecutionRequest request,
            AgentEvaluationTriggerExecutionResult result) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("status", result.status().name());
        fields.put("reason", result.reason().name());
        fields.put("datasetVersion", request.evaluationRequest().datasetVersion());
        fields.put("agentId", request.evaluationRequest().agentId());
        fields.put("agentVersion", request.evaluationRequest().agentVersion());
        fields.put("provider", request.evaluationRequest().provider());
        fields.put("model", request.evaluationRequest().modelId());
        return fields;
    }
}
