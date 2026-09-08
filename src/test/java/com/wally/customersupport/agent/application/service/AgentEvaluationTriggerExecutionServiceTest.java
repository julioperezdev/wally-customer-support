package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationApplicationService;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationExecutor;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRun;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRunRequest;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerAuthorizationReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerExecutionReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerExecutionRequest;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerExecutionStatus;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerRequest;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationTriggerExecutionGuard;
import org.junit.jupiter.api.Test;

class AgentEvaluationTriggerExecutionServiceTest {

    private static final String ENVIRONMENT = "prod";
    private static final String ACTOR = "evaluation-runner";
    private static final String IDEMPOTENCY_KEY = "run-request-001";
    private static final AgentEvaluationExecutor EXECUTOR = scenario -> null;
    private static final AgentEvaluationRunRequest EVALUATION_REQUEST = new AgentEvaluationRunRequest(
            "catalog-response-v1", "catalog-specialist", "v1", "mock", "deterministic-v1");

    @Test
    void deniesBeforeGuardAndEvaluationWhenAuthorizationFails() {
        AgentEvaluationApplicationService evaluationService = mock(AgentEvaluationApplicationService.class);
        AgentEvaluationTriggerExecutionGuard guard = mock(AgentEvaluationTriggerExecutionGuard.class);
        var service = service(false, evaluationService, guard);

        var result = service.execute(executionRequest(), EXECUTOR);

        assertThat(result.status()).isEqualTo(AgentEvaluationTriggerExecutionStatus.DENIED);
        assertThat(result.reason()).isEqualTo(AgentEvaluationTriggerExecutionReason.AUTHORIZATION_DENIED);
        assertThat(result.authorizationReason())
                .isEqualTo(AgentEvaluationTriggerAuthorizationReason.AUTHORIZER_DENIED);
        verify(guard, never()).tryAcquire(IDEMPOTENCY_KEY);
        verify(evaluationService, never()).execute(EVALUATION_REQUEST, EXECUTOR);
    }

    @Test
    void executesOnceAfterAuthorizationAndReturnsRunId() {
        AgentEvaluationApplicationService evaluationService = mock(AgentEvaluationApplicationService.class);
        AgentEvaluationTriggerExecutionGuard guard = mock(AgentEvaluationTriggerExecutionGuard.class);
        AgentEvaluationRun run = mock(AgentEvaluationRun.class);
        UUID runId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(guard.tryAcquire(IDEMPOTENCY_KEY)).thenReturn(true);
        when(evaluationService.execute(EVALUATION_REQUEST, EXECUTOR)).thenReturn(run);
        when(run.runId()).thenReturn(runId);
        var service = service(true, evaluationService, guard);

        var result = service.execute(executionRequest(), EXECUTOR);

        assertThat(result.status()).isEqualTo(AgentEvaluationTriggerExecutionStatus.COMPLETED);
        assertThat(result.reason()).isEqualTo(AgentEvaluationTriggerExecutionReason.EVALUATION_COMPLETED);
        assertThat(result.runId()).isEqualTo(runId);
        verify(guard).tryAcquire(IDEMPOTENCY_KEY);
        verify(evaluationService).execute(EVALUATION_REQUEST, EXECUTOR);
    }

    @Test
    void doesNotExecuteWhenIdempotencyKeyWasAlreadyClaimed() {
        AgentEvaluationApplicationService evaluationService = mock(AgentEvaluationApplicationService.class);
        AgentEvaluationTriggerExecutionGuard guard = mock(AgentEvaluationTriggerExecutionGuard.class);
        when(guard.tryAcquire(IDEMPOTENCY_KEY)).thenReturn(false);
        var service = service(true, evaluationService, guard);

        var result = service.execute(executionRequest(), EXECUTOR);

        assertThat(result.status()).isEqualTo(AgentEvaluationTriggerExecutionStatus.ALREADY_PROCESSED);
        assertThat(result.reason()).isEqualTo(AgentEvaluationTriggerExecutionReason.IDEMPOTENCY_ALREADY_CLAIMED);
        verify(evaluationService, never()).execute(EVALUATION_REQUEST, EXECUTOR);
    }

    @Test
    void returnsSanitizedFailureWhenEvaluationFails() {
        AgentEvaluationApplicationService evaluationService = mock(AgentEvaluationApplicationService.class);
        AgentEvaluationTriggerExecutionGuard guard = mock(AgentEvaluationTriggerExecutionGuard.class);
        when(guard.tryAcquire(IDEMPOTENCY_KEY)).thenReturn(true);
        when(evaluationService.execute(EVALUATION_REQUEST, EXECUTOR))
                .thenThrow(new IllegalStateException("sensitive provider details"));
        var service = service(true, evaluationService, guard);

        var result = service.execute(executionRequest(), EXECUTOR);

        assertThat(result.status()).isEqualTo(AgentEvaluationTriggerExecutionStatus.FAILED);
        assertThat(result.reason()).isEqualTo(AgentEvaluationTriggerExecutionReason.EVALUATION_FAILED);
        assertThat(result.toString()).doesNotContain("sensitive provider details");
    }

    @Test
    void failsClosedWhenIdempotencyGuardFails() {
        AgentEvaluationApplicationService evaluationService = mock(AgentEvaluationApplicationService.class);
        AgentEvaluationTriggerExecutionGuard guard = mock(AgentEvaluationTriggerExecutionGuard.class);
        when(guard.tryAcquire(IDEMPOTENCY_KEY)).thenThrow(new IllegalStateException("storage unavailable"));
        var service = service(true, evaluationService, guard);

        var result = service.execute(executionRequest(), EXECUTOR);

        assertThat(result.status()).isEqualTo(AgentEvaluationTriggerExecutionStatus.FAILED);
        assertThat(result.reason()).isEqualTo(AgentEvaluationTriggerExecutionReason.IDEMPOTENCY_GUARD_FAILED);
        verify(evaluationService, never()).execute(EVALUATION_REQUEST, EXECUTOR);
    }

    private static AgentEvaluationTriggerExecutionService service(
            boolean authorized,
            AgentEvaluationApplicationService evaluationService,
            AgentEvaluationTriggerExecutionGuard guard) {
        return new AgentEvaluationTriggerExecutionService(
                new AgentEvaluationTriggerAuthorizationService(ENVIRONMENT, request -> authorized),
                evaluationService,
                guard);
    }

    private static AgentEvaluationTriggerExecutionRequest executionRequest() {
        return new AgentEvaluationTriggerExecutionRequest(
                request(true), EVALUATION_REQUEST);
    }

    private static AgentEvaluationTriggerRequest request(boolean authorized) {
        return new AgentEvaluationTriggerRequest(
                authorized ? ACTOR : "other-actor",
                ENVIRONMENT,
                AgentEvaluationTriggerAuthorizationService.EVALUATION_EXECUTE_CAPABILITY,
                IDEMPOTENCY_KEY);
    }
}
