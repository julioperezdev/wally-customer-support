package com.wally.customersupport.agent.infrastructure.http;

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
import com.wally.customersupport.agent.application.service.AgentRegistryQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AgentRegistryControllerTest {

    private static final String ACTOR = "synthetic-operator";

    @Mock
    private AgentEvaluationControlPlaneAccessService accessService;

    @Mock
    private AgentRegistryQueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AgentRegistryController(accessService, queryService))
                .setControllerAdvice(new AgentEvaluationControlPlaneExceptionHandler())
                .build();
    }

    @Test
    void deniesRegistryWithoutItsDedicatedCapability() throws Exception {
        when(accessService.authorizeRegistry(ACTOR)).thenReturn(denied());

        mockMvc.perform(get("/internal/agent-registry/agents").principal(() -> ACTOR))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(queryService);
    }

    @Test
    void returnsFilteredReadOnlyRegistryViewAfterAuthorization() throws Exception {
        when(accessService.authorizeRegistry(ACTOR)).thenReturn(authorized());
        when(queryService.search(any())).thenReturn(List.of());

        mockMvc.perform(get("/internal/agent-registry/agents")
                        .principal(() -> ACTOR)
                        .param("environment", "prod")
                        .param("channel", "telegram")
                        .param("useCase", "catalog-search")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    private static AgentEvaluationControlPlaneAccessDecision authorized() {
        return new AgentEvaluationControlPlaneAccessDecision(
                AgentEvaluationControlPlaneAccessStatus.AUTHORIZED,
                ACTOR,
                "prod",
                AgentEvaluationControlPlaneAccessService.REGISTRY_READ_CAPABILITY,
                AgentEvaluationControlPlaneAccessReason.AUTHORIZED);
    }

    private static AgentEvaluationControlPlaneAccessDecision denied() {
        return new AgentEvaluationControlPlaneAccessDecision(
                AgentEvaluationControlPlaneAccessStatus.DENIED,
                ACTOR,
                "prod",
                AgentEvaluationControlPlaneAccessService.REGISTRY_READ_CAPABILITY,
                AgentEvaluationControlPlaneAccessReason.AUTHORIZER_DENIED);
    }
}
