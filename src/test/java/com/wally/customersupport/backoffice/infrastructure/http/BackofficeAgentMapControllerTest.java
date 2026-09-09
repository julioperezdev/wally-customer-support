package com.wally.customersupport.backoffice.infrastructure.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessStatus;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import com.wally.customersupport.backoffice.application.model.BackofficeAgentMap;
import com.wally.customersupport.backoffice.application.service.BackofficeAgentMapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BackofficeAgentMapControllerTest {

    private static final String ACTOR = "synthetic-operator";

    @Mock
    private AgentEvaluationControlPlaneAccessService accessService;

    @Mock
    private BackofficeAgentMapService mapService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BackofficeAgentMapController(accessService, mapService)).build();
    }

    @Test
    void deniesMapWithoutRegistryReadCapabilityAndReturnsNoMetadata() throws Exception {
        when(accessService.authorizeRegistry(ACTOR, "prod")).thenReturn(denied());

        mockMvc.perform(get("/internal/backoffice/agent-map").principal(() -> ACTOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(mapService);
    }

    @Test
    void returnsReadOnlyMapAfterAuthorization() throws Exception {
        when(accessService.authorizeRegistry(ACTOR, "prod")).thenReturn(authorized());
        when(mapService.describe(any())).thenReturn(new BackofficeAgentMap(
                java.time.Instant.parse("2026-09-09T12:00:00Z"),
                new com.wally.customersupport.backoffice.application.model.BackofficeAgentMapQuery(
                        "prod", "telegram", "catalog-search", null),
                List.of(), 0, false));

        mockMvc.perform(get("/internal/backoffice/agent-map")
                        .principal(() -> ACTOR)
                        .param("channel", "telegram")
                        .param("useCase", "catalog-search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.useCases").isArray())
                .andExpect(jsonPath("$.evidenceRunsScanned").value(0));
    }

    private static AgentEvaluationControlPlaneAccessDecision authorized() {
        return new AgentEvaluationControlPlaneAccessDecision(
                AgentEvaluationControlPlaneAccessStatus.AUTHORIZED, ACTOR, "prod",
                AgentEvaluationControlPlaneAccessService.REGISTRY_READ_CAPABILITY,
                AgentEvaluationControlPlaneAccessReason.AUTHORIZED);
    }

    private static AgentEvaluationControlPlaneAccessDecision denied() {
        return new AgentEvaluationControlPlaneAccessDecision(
                AgentEvaluationControlPlaneAccessStatus.DENIED, ACTOR, "prod",
                AgentEvaluationControlPlaneAccessService.REGISTRY_READ_CAPABILITY,
                AgentEvaluationControlPlaneAccessReason.AUTHORIZER_DENIED);
    }
}
