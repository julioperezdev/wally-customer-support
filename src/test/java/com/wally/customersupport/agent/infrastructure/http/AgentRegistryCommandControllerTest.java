package com.wally.customersupport.agent.infrastructure.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.agent.application.registry.AgentLifecycleTransitionCommand;
import com.wally.customersupport.agent.application.registry.AgentRegistryMutationReason;
import com.wally.customersupport.agent.application.registry.AgentRegistryMutationResult;
import com.wally.customersupport.agent.application.registry.AgentRegistryMutationStatus;
import com.wally.customersupport.agent.application.service.AgentRegistryCommandService;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AgentRegistryCommandControllerTest {

    @Mock
    private AgentRegistryCommandService commandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AgentRegistryCommandController(commandService))
                .setControllerAdvice(new AgentEvaluationControlPlaneExceptionHandler())
                .build();
    }

    @Test
    void createsDraftAndReturnsOnlySanitizedMutationMetadata() throws Exception {
        when(commandService.createDraft(any(), any(), any())).thenReturn(
                new AgentRegistryMutationResult(
                        AgentRegistryMutationStatus.CREATED,
                        AgentRegistryMutationReason.DRAFT_PERSISTED,
                        "support-specialist",
                        1,
                        AgentLifecycleState.DRAFT,
                        Instant.parse("2026-09-13T03:00:00Z"),
                        Instant.parse("2026-09-13T03:00:00Z")));

        mockMvc.perform(post("/internal/agent-registry/agents/support-specialist/versions")
                        .principal(() -> "agent-operator")
                        .header("Idempotency-Key", "authoring-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Support\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.agentId").value("support-specialist"))
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.systemPromptHash").doesNotExist());
    }

    @Test
    void rejectsMissingIdempotencyBeforeCallingTheCommandService() throws Exception {
        mockMvc.perform(post("/internal/agent-registry/agents/support-specialist/versions")
                        .principal(() -> "agent-operator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        verifyNoInteractions(commandService);
    }

    @Test
    void acceptsComparableEvaluationRunReferencesForLifecycleReview() throws Exception {
        UUID baselineRunId = UUID.fromString("00000000-0000-0000-0000-000000000041");
        UUID candidateRunId = UUID.fromString("00000000-0000-0000-0000-000000000042");
        when(commandService.transition(any(), any(), any())).thenReturn(
                new AgentRegistryMutationResult(
                        AgentRegistryMutationStatus.TRANSITIONED,
                        AgentRegistryMutationReason.LIFECYCLE_TRANSITIONED,
                        "support-specialist",
                        2,
                        AgentLifecycleState.EVALUATED,
                        Instant.parse("2026-09-13T03:00:00Z"),
                        Instant.parse("2026-09-13T03:01:00Z")));

        mockMvc.perform(post("/internal/agent-registry/agents/support-specialist/versions/2/lifecycle")
                        .principal(() -> "agent-operator")
                        .header("Idempotency-Key", "evaluation-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"targetState":"EVALUATED","reason":"reviewed",
                                 "baselineEvaluationRunId":"%s","candidateEvaluationRunId":"%s"}
                                """.formatted(baselineRunId, candidateRunId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EVALUATED"));

        var command = forClass(AgentLifecycleTransitionCommand.class);
        verify(commandService).transition(command.capture(), org.mockito.ArgumentMatchers.eq("agent-operator"),
                org.mockito.ArgumentMatchers.eq("evaluation-001"));
        org.assertj.core.api.Assertions.assertThat(command.getValue().baselineEvaluationRunId())
                .isEqualTo(baselineRunId);
        org.assertj.core.api.Assertions.assertThat(command.getValue().candidateEvaluationRunId())
                .isEqualTo(candidateRunId);
    }
}
