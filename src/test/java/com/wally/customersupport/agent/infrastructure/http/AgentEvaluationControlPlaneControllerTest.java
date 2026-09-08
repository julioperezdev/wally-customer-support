package com.wally.customersupport.agent.infrastructure.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessStatus;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPage;
import com.wally.customersupport.agent.application.service.AgentEvaluationComparisonApplicationService;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import com.wally.customersupport.agent.application.service.AgentEvaluationEvidenceExportApplicationService;
import com.wally.customersupport.agent.application.service.AgentEvaluationHistoryQueryService;
import com.wally.customersupport.agent.application.service.IncompatibleEvaluationDatasetException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AgentEvaluationControlPlaneControllerTest {

    private static final String ACTOR = "synthetic-actor";
    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Mock
    private AgentEvaluationControlPlaneAccessService accessService;

    @Mock
    private AgentEvaluationHistoryQueryService historyQueryService;

    @Mock
    private AgentEvaluationComparisonApplicationService comparisonService;

    @Mock
    private AgentEvaluationEvidenceExportApplicationService evidenceExportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AgentEvaluationControlPlaneController(
                accessService, historyQueryService, comparisonService, evidenceExportService))
                .setControllerAdvice(new AgentEvaluationControlPlaneExceptionHandler())
                .build();
    }

    @Test
    void deniesByDefaultBeforeCallingAnyEvaluationService() throws Exception {
        when(accessService.authorize(ACTOR)).thenReturn(denied());

        mockMvc.perform(get("/internal/agent-evaluations/runs")
                        .principal(() -> ACTOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(historyQueryService, comparisonService, evidenceExportService);
    }

    @Test
    void returnsBoundedFilteredHistoryAfterAuthorization() throws Exception {
        when(accessService.authorize(ACTOR)).thenReturn(authorized());
        when(historyQueryService.search(any(), any())).thenReturn(
                new AgentEvaluationHistoryPage(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/internal/agent-evaluations/runs")
                        .principal(() -> ACTOR)
                        .param("datasetVersion", "catalog-response-v1")
                        .param("completedFrom", "2026-09-08T00:00:00Z")
                        .param("completedTo", "2026-09-08T23:59:59Z")
                        .param("page", "0")
                        .param("size", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.pageNumber").value(0));

        verify(historyQueryService).search(any(), any());
    }

    @Test
    void returnsNotFoundForAnUnknownRun() throws Exception {
        when(accessService.authorize(ACTOR)).thenReturn(authorized());
        when(historyQueryService.findById(RUN_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/internal/agent-evaluations/runs/{runId}", RUN_ID)
                        .principal(() -> ACTOR))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"));
    }

    @Test
    void mapsIncompatibleComparisonToAStableConflict() throws Exception {
        when(accessService.authorize(ACTOR)).thenReturn(authorized());
        when(comparisonService.compare(RUN_ID, UUID.fromString("00000000-0000-0000-0000-000000000012")))
                .thenThrow(new IncompatibleEvaluationDatasetException());

        mockMvc.perform(get("/internal/agent-evaluations/comparisons")
                        .principal(() -> ACTOR)
                        .param("baselineRunId", RUN_ID.toString())
                        .param("candidateRunId", "00000000-0000-0000-0000-000000000012"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INCOMPATIBLE_DATASET"));
    }

    @Test
    void rejectsMalformedIdentifiersAndDoesNotCallTheApplicationService() throws Exception {
        mockMvc.perform(get("/internal/agent-evaluations/comparisons")
                        .principal(() -> ACTOR)
                        .param("baselineRunId", "not-a-uuid")
                        .param("candidateRunId", RUN_ID.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(comparisonService);
    }

    @Test
    void rejectsInvalidPageLimitsBeforeQueryingHistory() throws Exception {
        when(accessService.authorize(ACTOR)).thenReturn(authorized());

        mockMvc.perform(get("/internal/agent-evaluations/runs")
                        .principal(() -> ACTOR)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(historyQueryService);
    }

    private static AgentEvaluationControlPlaneAccessDecision authorized() {
        return new AgentEvaluationControlPlaneAccessDecision(
                AgentEvaluationControlPlaneAccessStatus.AUTHORIZED,
                ACTOR,
                "prod",
                AgentEvaluationControlPlaneAccessService.EVALUATION_READ_CAPABILITY,
                AgentEvaluationControlPlaneAccessReason.AUTHORIZED);
    }

    private static AgentEvaluationControlPlaneAccessDecision denied() {
        return new AgentEvaluationControlPlaneAccessDecision(
                AgentEvaluationControlPlaneAccessStatus.DENIED,
                ACTOR,
                "prod",
                AgentEvaluationControlPlaneAccessService.EVALUATION_READ_CAPABILITY,
                AgentEvaluationControlPlaneAccessReason.AUTHORIZER_DENIED);
    }
}
