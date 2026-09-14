package com.wally.customersupport.backoffice.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessStatus;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import org.junit.jupiter.api.Test;

class BackofficeAccessServiceTest {

    @Test
    void remainsClosedWhenNotExplicitlyEnabled() {
        BackofficeAccessService service = new BackofficeAccessService(false);

        assertThat(service.authorize("backoffice.catalog.read"))
                .usingRecursiveComparison()
                .isEqualTo(new BackofficeAccessService.Decision(false, 404, "BACKOFFICE_DISABLED"));
    }

    @Test
    void requiresCognitoAuthorizationWhenEnabled() {
        BackofficeAccessService service = new BackofficeAccessService(true);

        assertThat(service.authorize("backoffice.catalog.read")).isEqualTo(
                new BackofficeAccessService.Decision(false, 403, "BACKOFFICE_AUTHORIZATION_REQUIRED"));
    }

    @Test
    void allowsAccessAfterCognitoAuthorization() {
        AgentEvaluationControlPlaneAccessService controlPlane = mock(AgentEvaluationControlPlaneAccessService.class);
        when(controlPlane.authorizeBackoffice(eq("cognito-operator"), eq("backoffice.catalog.read")))
                .thenReturn(new AgentEvaluationControlPlaneAccessDecision(
                        AgentEvaluationControlPlaneAccessStatus.AUTHORIZED,
                        "cognito-operator",
                        "prod",
                        "backoffice.catalog.read",
                        AgentEvaluationControlPlaneAccessReason.AUTHORIZED));

        BackofficeAccessService service = new BackofficeAccessService(
                true, controlPlane);

        assertThat(service.authorize("backoffice.catalog.read", "cognito-operator")).isEqualTo(
                new BackofficeAccessService.Decision(true, 200, "backoffice.catalog.read"));
    }
}
