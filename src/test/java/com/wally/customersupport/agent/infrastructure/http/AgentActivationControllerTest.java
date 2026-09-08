package com.wally.customersupport.agent.infrastructure.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.wally.customersupport.agent.application.activation.AgentActivationMutationReason;
import com.wally.customersupport.agent.application.activation.AgentActivationMutationResult;
import com.wally.customersupport.agent.application.activation.AgentActivationMutationStatus;
import com.wally.customersupport.agent.application.service.AgentActivationCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AgentActivationControllerTest {

    private static final String ACTOR = "activation-operator";
    private static final String IDEMPOTENCY_KEY = "activation-request-001";
    private static final String REQUEST = """
            {
              "agentId": "catalog-specialist",
              "agentVersion": 1,
              "environment": "prod",
              "channel": "telegram",
              "useCase": "catalog-search",
              "reason": "initial rollout",
              "rolloutPercentage": 100,
              "enabled": true,
              "approvalReference": "approval-1",
              "operationalApprovalReference": "ops-approval-1"
            }
            """;

    @Mock
    private AgentActivationCommandService commandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AgentActivationController(commandService, false))
                .setControllerAdvice(new AgentEvaluationControlPlaneExceptionHandler())
                .build();
    }

    @Test
    void keepsActivationClosedByDefaultAndReturnsNoSensitiveFields() throws Exception {
        when(commandService.activate(any(), any(), any(), any(Boolean.class))).thenReturn(
                new AgentActivationMutationResult(
                        AgentActivationMutationStatus.DISABLED,
                        AgentActivationMutationReason.FEATURE_DISABLED,
                        "catalog-specialist",
                        null,
                        null,
                        null,
                        null,
                        null));

        mockMvc.perform(post("/internal/agent-registry/activations")
                        .principal(() -> ACTOR)
                        .header("Idempotency-Key", IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.reason").value("FEATURE_DISABLED"))
                .andExpect(jsonPath("$.approvalReference").doesNotExist())
                .andExpect(jsonPath("$.operationalApprovalReference").doesNotExist())
                .andExpect(jsonPath("$.activatedBy").doesNotExist());

        verify(commandService).activate(any(), any(), any(), any(Boolean.class));
    }

    @Test
    void rejectsMissingIdempotencyKeyBeforeCallingTheApplicationService() throws Exception {
        mockMvc.perform(post("/internal/agent-registry/activations")
                        .principal(() -> ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(commandService);
    }

    @Test
    void mapsDuplicateActivationToConflict() throws Exception {
        when(commandService.activate(any(), any(), any(), any(Boolean.class))).thenReturn(
                new AgentActivationMutationResult(
                        AgentActivationMutationStatus.ALREADY_PROCESSED,
                        AgentActivationMutationReason.IDEMPOTENCY_ALREADY_CLAIMED,
                        "catalog-specialist",
                        1,
                        "prod",
                        "telegram",
                        "catalog-search",
                        null));

        mockMvc.perform(post("/internal/agent-registry/activations")
                        .principal(() -> ACTOR)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ALREADY_PROCESSED"));
    }
}
