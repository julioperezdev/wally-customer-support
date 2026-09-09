package com.wally.customersupport.backoffice.infrastructure.http;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessStatus;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import com.wally.customersupport.featureflag.application.FeatureFlagSnapshotView;
import com.wally.customersupport.featureflag.application.service.FeatureFlagRuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class BackofficeFeatureFlagControllerTest {

    @Mock
    private AgentEvaluationControlPlaneAccessService accessService;

    @Mock
    private FeatureFlagRuntimeService runtime;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BackofficeFeatureFlagController(accessService, runtime)).build();
    }

    @Test
    void deniesFeatureFlagReadWithoutCapability() throws Exception {
        when(accessService.authorizeFeatureFlags("operator")).thenReturn(new AgentEvaluationControlPlaneAccessDecision(
                AgentEvaluationControlPlaneAccessStatus.DENIED,
                "operator",
                "prod",
                AgentEvaluationControlPlaneAccessService.FEATURE_FLAGS_READ_CAPABILITY,
                AgentEvaluationControlPlaneAccessReason.AUTHORIZER_DENIED));

        mockMvc.perform(get("/internal/backoffice/feature-flags").principal(() -> "operator"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(runtime);
    }

    @Test
    void returnsSanitizedCurrentSnapshotAfterAuthorization() throws Exception {
        when(accessService.authorizeFeatureFlags("operator")).thenReturn(new AgentEvaluationControlPlaneAccessDecision(
                AgentEvaluationControlPlaneAccessStatus.AUTHORIZED,
                "operator",
                "prod",
                AgentEvaluationControlPlaneAccessService.FEATURE_FLAGS_READ_CAPABILITY,
                AgentEvaluationControlPlaneAccessReason.AUTHORIZED));
        when(runtime.view()).thenReturn(new FeatureFlagSnapshotView(
                "prod", "v1", Instant.parse("2026-09-09T12:00:00Z"),
                Instant.parse("2026-09-09T12:00:00Z"), false, List.of(), List.of()));

        mockMvc.perform(get("/internal/backoffice/feature-flags").principal(() -> "operator"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveVersion").value("v1"))
                .andExpect(jsonPath("$.flags").isArray());
    }
}
