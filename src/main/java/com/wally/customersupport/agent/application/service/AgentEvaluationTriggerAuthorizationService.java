package com.wally.customersupport.agent.application.service;

import java.util.Objects;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerAuthorizationDecision;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerAuthorizationReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerAuthorizationStatus;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerRequest;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationTriggerAuthorizer;

/** Provider-neutral, fail-closed authorization boundary for a future trigger. */
public class AgentEvaluationTriggerAuthorizationService {

    public static final String EVALUATION_EXECUTE_CAPABILITY = "agent-evaluation.execute";

    private final String allowedEnvironment;
    private final AgentEvaluationTriggerAuthorizer authorizer;

    public AgentEvaluationTriggerAuthorizationService(
            String allowedEnvironment,
            AgentEvaluationTriggerAuthorizer authorizer) {
        this.allowedEnvironment = required(allowedEnvironment, "allowedEnvironment");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    public AgentEvaluationTriggerAuthorizationDecision authorize(
            AgentEvaluationTriggerRequest request) {
        AgentEvaluationTriggerRequest triggerRequest = Objects.requireNonNull(request, "request");
        if (isBlank(triggerRequest.actorId())) {
            return denied(triggerRequest, AgentEvaluationTriggerAuthorizationReason.MISSING_ACTOR);
        }
        if (isBlank(triggerRequest.environment())) {
            return denied(triggerRequest, AgentEvaluationTriggerAuthorizationReason.MISSING_ENVIRONMENT);
        }
        if (isBlank(triggerRequest.capability())) {
            return denied(triggerRequest, AgentEvaluationTriggerAuthorizationReason.MISSING_CAPABILITY);
        }
        if (isBlank(triggerRequest.idempotencyKey())) {
            return denied(triggerRequest, AgentEvaluationTriggerAuthorizationReason.MISSING_IDEMPOTENCY_KEY);
        }
        if (!EVALUATION_EXECUTE_CAPABILITY.equals(triggerRequest.capability())) {
            return denied(triggerRequest, AgentEvaluationTriggerAuthorizationReason.CAPABILITY_NOT_ALLOWED);
        }
        if (!allowedEnvironment.equals(triggerRequest.environment())) {
            return denied(triggerRequest, AgentEvaluationTriggerAuthorizationReason.ENVIRONMENT_NOT_ALLOWED);
        }
        try {
            if (authorizer.authorize(triggerRequest)) {
                return new AgentEvaluationTriggerAuthorizationDecision(
                        AgentEvaluationTriggerAuthorizationStatus.AUTHORIZED,
                        triggerRequest.actorId(),
                        triggerRequest.environment(),
                        triggerRequest.capability(),
                        AgentEvaluationTriggerAuthorizationReason.AUTHORIZED);
            }
        } catch (RuntimeException ignored) {
            // Fail closed and keep provider errors out of the application contract.
        }
        return denied(triggerRequest, AgentEvaluationTriggerAuthorizationReason.AUTHORIZER_DENIED);
    }

    private static AgentEvaluationTriggerAuthorizationDecision denied(
            AgentEvaluationTriggerRequest request,
            AgentEvaluationTriggerAuthorizationReason reason) {
        return new AgentEvaluationTriggerAuthorizationDecision(
                AgentEvaluationTriggerAuthorizationStatus.DENIED,
                request.actorId(),
                request.environment(),
                request.capability(),
                reason);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
