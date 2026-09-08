package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerAuthorizationReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerAuthorizationStatus;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerRequest;
import org.junit.jupiter.api.Test;

class AgentEvaluationTriggerAuthorizationServiceTest {

    private static final String ENVIRONMENT = "prod";
    private static final String ACTOR = "evaluation-runner";
    private static final String IDEMPOTENCY_KEY = "run-request-001";

    @Test
    void authorizesOnlyTheExactCapabilityAndEnvironmentWhenProviderConfirms() {
        var service = new AgentEvaluationTriggerAuthorizationService(ENVIRONMENT, request -> true);

        var decision = service.authorize(request(ENVIRONMENT,
                AgentEvaluationTriggerAuthorizationService.EVALUATION_EXECUTE_CAPABILITY));

        assertThat(decision.status()).isEqualTo(AgentEvaluationTriggerAuthorizationStatus.AUTHORIZED);
        assertThat(decision.reason()).isEqualTo(AgentEvaluationTriggerAuthorizationReason.AUTHORIZED);
        assertThat(decision.authorized()).isTrue();
        assertThat(decision.actorId()).isEqualTo(ACTOR);
    }

    @Test
    void deniesMissingTriggerMetadata() {
        var service = new AgentEvaluationTriggerAuthorizationService(ENVIRONMENT, request -> true);

        var decision = service.authorize(new AgentEvaluationTriggerRequest(
                ACTOR, ENVIRONMENT, null, null));

        assertThat(decision.status()).isEqualTo(AgentEvaluationTriggerAuthorizationStatus.DENIED);
        assertThat(decision.reason()).isEqualTo(AgentEvaluationTriggerAuthorizationReason.MISSING_CAPABILITY);
        assertThat(decision.authorized()).isFalse();
    }

    @Test
    void deniesUnsupportedCapabilityAndEnvironmentBeforeCallingProvider() {
        var providerCalls = new int[1];
        var service = new AgentEvaluationTriggerAuthorizationService(ENVIRONMENT, request -> {
            providerCalls[0]++;
            return true;
        });

        var capabilityDecision = service.authorize(request(ENVIRONMENT, "agent-evaluation.read"));
        var environmentDecision = service.authorize(request("test",
                AgentEvaluationTriggerAuthorizationService.EVALUATION_EXECUTE_CAPABILITY));

        assertThat(capabilityDecision.reason())
                .isEqualTo(AgentEvaluationTriggerAuthorizationReason.CAPABILITY_NOT_ALLOWED);
        assertThat(environmentDecision.reason())
                .isEqualTo(AgentEvaluationTriggerAuthorizationReason.ENVIRONMENT_NOT_ALLOWED);
        assertThat(providerCalls[0]).isZero();
    }

    @Test
    void deniesByDefaultWhenProviderDoesNotConfirm() {
        var service = new AgentEvaluationTriggerAuthorizationService(ENVIRONMENT, request -> false);

        var decision = service.authorize(request(ENVIRONMENT,
                AgentEvaluationTriggerAuthorizationService.EVALUATION_EXECUTE_CAPABILITY));

        assertThat(decision.status()).isEqualTo(AgentEvaluationTriggerAuthorizationStatus.DENIED);
        assertThat(decision.reason()).isEqualTo(AgentEvaluationTriggerAuthorizationReason.AUTHORIZER_DENIED);
    }

    @Test
    void deniesByDefaultWhenProviderFails() {
        var service = new AgentEvaluationTriggerAuthorizationService(ENVIRONMENT, request -> {
            throw new IllegalStateException("provider unavailable");
        });

        var decision = service.authorize(request(ENVIRONMENT,
                AgentEvaluationTriggerAuthorizationService.EVALUATION_EXECUTE_CAPABILITY));

        assertThat(decision.status()).isEqualTo(AgentEvaluationTriggerAuthorizationStatus.DENIED);
        assertThat(decision.reason()).isEqualTo(AgentEvaluationTriggerAuthorizationReason.AUTHORIZER_DENIED);
    }

    private static AgentEvaluationTriggerRequest request(String environment, String capability) {
        return new AgentEvaluationTriggerRequest(ACTOR, environment, capability, IDEMPOTENCY_KEY);
    }
}
