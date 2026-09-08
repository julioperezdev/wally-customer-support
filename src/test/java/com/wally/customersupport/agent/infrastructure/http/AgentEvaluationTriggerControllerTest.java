package com.wally.customersupport.agent.infrastructure.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationExecutor;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerAuthorizationReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerExecutionReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerExecutionResult;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerExecutionStatus;
import com.wally.customersupport.agent.application.service.AgentEvaluationTriggerExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AgentEvaluationTriggerControllerTest {

    private static final String ACTOR = "evaluation-operator";
    private static final String IDEMPOTENCY_KEY = "evaluation-request-001";
    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final String REQUEST = """
            {
              "datasetVersion": "catalog-response-v1",
              "agentId": "catalog-specialist",
              "agentVersion": "v1",
              "provider": "mock",
              "modelId": "deterministic-v1"
            }
            """;

    @Mock
    private AgentEvaluationTriggerExecutionService triggerService;

    @Mock
    private AgentEvaluationExecutor executor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AgentEvaluationTriggerController(triggerService, executor, "prod"))
                .setControllerAdvice(new AgentEvaluationControlPlaneExceptionHandler())
                .build();
    }

    @Test
    void returnsRunIdWhenEvaluationCompletes() throws Exception {
        when(triggerService.execute(any(), any())).thenReturn(new AgentEvaluationTriggerExecutionResult(
                AgentEvaluationTriggerExecutionStatus.COMPLETED,
                AgentEvaluationTriggerExecutionReason.EVALUATION_COMPLETED,
                null,
                RUN_ID));

        mockMvc.perform(post("/internal/agent-evaluations/runs")
                        .principal(() -> ACTOR)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.reason").value("EVALUATION_COMPLETED"))
                .andExpect(jsonPath("$.runId").value(RUN_ID.toString()));

        verify(triggerService).execute(any(), any());
    }

    @Test
    void mapsAlreadyClaimedKeyToConflictWithoutExposingInternalDetails() throws Exception {
        when(triggerService.execute(any(), any())).thenReturn(new AgentEvaluationTriggerExecutionResult(
                AgentEvaluationTriggerExecutionStatus.ALREADY_PROCESSED,
                AgentEvaluationTriggerExecutionReason.IDEMPOTENCY_ALREADY_CLAIMED,
                null,
                null));

        mockMvc.perform(post("/internal/agent-evaluations/runs")
                        .principal(() -> ACTOR)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ALREADY_PROCESSED"))
                .andExpect(jsonPath("$.runId").doesNotExist())
                .andExpect(jsonPath("$.authorizationReason").doesNotExist());
    }

    @Test
    void mapsApplicationAuthorizationDenialToForbidden() throws Exception {
        when(triggerService.execute(any(), any())).thenReturn(new AgentEvaluationTriggerExecutionResult(
                AgentEvaluationTriggerExecutionStatus.DENIED,
                AgentEvaluationTriggerExecutionReason.AUTHORIZATION_DENIED,
                AgentEvaluationTriggerAuthorizationReason.AUTHORIZER_DENIED,
                null));

        mockMvc.perform(post("/internal/agent-evaluations/runs")
                        .principal(() -> ACTOR)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("DENIED"))
                .andExpect(jsonPath("$.authorizationReason").doesNotExist());
    }

    @Test
    void rejectsMissingIdempotencyKeyBeforeCallingApplicationService() throws Exception {
        mockMvc.perform(post("/internal/agent-evaluations/runs")
                        .principal(() -> ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(triggerService, executor);
    }

    @Test
    void rejectsMalformedBodyWithStableError() throws Exception {
        mockMvc.perform(post("/internal/agent-evaluations/runs")
                        .principal(() -> ACTOR)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(triggerService, executor);
    }
}
