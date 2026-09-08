package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessRequest;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessStatus;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationControlPlaneAuthorizer;
import org.junit.jupiter.api.Test;

class AgentEvaluationControlPlaneAccessServiceTest {

    @Test
    void authorizesOnlyTheConfiguredEnvironmentAndReadCapability() {
        AgentEvaluationControlPlaneAuthorizer authorizer = mock(AgentEvaluationControlPlaneAuthorizer.class);
        AgentEvaluationControlPlaneAccessRequest request = new AgentEvaluationControlPlaneAccessRequest(
                "synthetic-actor", "prod", AgentEvaluationControlPlaneAccessService.EVALUATION_READ_CAPABILITY);
        when(authorizer.authorize(request)).thenReturn(true);

        var decision = new AgentEvaluationControlPlaneAccessService("prod", authorizer)
                .authorize(request);

        assertThat(decision.status()).isEqualTo(AgentEvaluationControlPlaneAccessStatus.AUTHORIZED);
        assertThat(decision.reason()).isEqualTo(AgentEvaluationControlPlaneAccessReason.AUTHORIZED);
    }

    @Test
    void deniesMissingActorBeforeCallingTheProvider() {
        AgentEvaluationControlPlaneAuthorizer authorizer = mock(AgentEvaluationControlPlaneAuthorizer.class);

        var decision = new AgentEvaluationControlPlaneAccessService("prod", authorizer)
                .authorize((String) null);

        assertThat(decision.status()).isEqualTo(AgentEvaluationControlPlaneAccessStatus.DENIED);
        assertThat(decision.reason()).isEqualTo(AgentEvaluationControlPlaneAccessReason.MISSING_ACTOR);
        org.mockito.Mockito.verifyNoInteractions(authorizer);
    }

    @Test
    void deniesUnexpectedCapabilityAndEnvironmentBeforeCallingTheProvider() {
        AgentEvaluationControlPlaneAuthorizer authorizer = mock(AgentEvaluationControlPlaneAuthorizer.class);
        var service = new AgentEvaluationControlPlaneAccessService("prod", authorizer);

        var capabilityDecision = service.authorize(new AgentEvaluationControlPlaneAccessRequest(
                "synthetic-actor", "prod", "agent-evaluation.execute"));
        var environmentDecision = service.authorize(new AgentEvaluationControlPlaneAccessRequest(
                "synthetic-actor", "test", AgentEvaluationControlPlaneAccessService.EVALUATION_READ_CAPABILITY));

        assertThat(capabilityDecision.reason()).isEqualTo(AgentEvaluationControlPlaneAccessReason.CAPABILITY_NOT_ALLOWED);
        assertThat(environmentDecision.reason()).isEqualTo(AgentEvaluationControlPlaneAccessReason.ENVIRONMENT_NOT_ALLOWED);
        org.mockito.Mockito.verifyNoInteractions(authorizer);
    }

    @Test
    void failsClosedWhenTheProviderRejectsOrFails() {
        AgentEvaluationControlPlaneAuthorizer authorizer = mock(AgentEvaluationControlPlaneAuthorizer.class);
        var request = new AgentEvaluationControlPlaneAccessRequest(
                "synthetic-actor", "prod", AgentEvaluationControlPlaneAccessService.EVALUATION_READ_CAPABILITY);
        when(authorizer.authorize(request)).thenReturn(false);

        var denied = new AgentEvaluationControlPlaneAccessService("prod", authorizer).authorize(request);

        assertThat(denied.reason()).isEqualTo(AgentEvaluationControlPlaneAccessReason.AUTHORIZER_DENIED);

        when(authorizer.authorize(request)).thenThrow(new IllegalStateException("provider unavailable"));
        var failed = new AgentEvaluationControlPlaneAccessService("prod", authorizer).authorize(request);

        assertThat(failed.status()).isEqualTo(AgentEvaluationControlPlaneAccessStatus.DENIED);
        assertThat(failed.reason()).isEqualTo(AgentEvaluationControlPlaneAccessReason.AUTHORIZER_DENIED);
    }
}
