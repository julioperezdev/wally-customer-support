package com.wally.customersupport.agent.application.service;

import java.util.Objects;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessDecision;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessRequest;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessStatus;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationControlPlaneAuthorizer;

/** Fail-closed access boundary for read-only evaluation control-plane operations. */
public class AgentEvaluationControlPlaneAccessService {

    public static final String EVALUATION_READ_CAPABILITY = "agent-evaluation.read";
    public static final String REGISTRY_READ_CAPABILITY = "agent-registry.read";

    private final String allowedEnvironment;
    private final AgentEvaluationControlPlaneAuthorizer authorizer;

    public AgentEvaluationControlPlaneAccessService(
            String allowedEnvironment,
            AgentEvaluationControlPlaneAuthorizer authorizer) {
        this.allowedEnvironment = required(allowedEnvironment, "allowedEnvironment");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    /** Builds the internal request without accepting an environment from HTTP callers. */
    public AgentEvaluationControlPlaneAccessDecision authorize(String actorId) {
        return authorize(new AgentEvaluationControlPlaneAccessRequest(
                actorId,
                allowedEnvironment,
                EVALUATION_READ_CAPABILITY));
    }

    /** Builds an internal registry request without accepting an environment from HTTP callers. */
    public AgentEvaluationControlPlaneAccessDecision authorizeRegistry(String actorId) {
        return authorize(new AgentEvaluationControlPlaneAccessRequest(
                actorId,
                allowedEnvironment,
                REGISTRY_READ_CAPABILITY));
    }

    public AgentEvaluationControlPlaneAccessDecision authorize(
            AgentEvaluationControlPlaneAccessRequest request) {
        AgentEvaluationControlPlaneAccessRequest accessRequest = Objects.requireNonNull(request, "request");
        if (isBlank(accessRequest.actorId())) {
            return denied(accessRequest, AgentEvaluationControlPlaneAccessReason.MISSING_ACTOR);
        }
        if (isBlank(accessRequest.environment())) {
            return denied(accessRequest, AgentEvaluationControlPlaneAccessReason.MISSING_ENVIRONMENT);
        }
        if (isBlank(accessRequest.capability())) {
            return denied(accessRequest, AgentEvaluationControlPlaneAccessReason.MISSING_CAPABILITY);
        }
        if (!EVALUATION_READ_CAPABILITY.equals(accessRequest.capability())
                && !REGISTRY_READ_CAPABILITY.equals(accessRequest.capability())) {
            return denied(accessRequest, AgentEvaluationControlPlaneAccessReason.CAPABILITY_NOT_ALLOWED);
        }
        if (!allowedEnvironment.equals(accessRequest.environment())) {
            return denied(accessRequest, AgentEvaluationControlPlaneAccessReason.ENVIRONMENT_NOT_ALLOWED);
        }
        try {
            if (authorizer.authorize(accessRequest)) {
                return new AgentEvaluationControlPlaneAccessDecision(
                        AgentEvaluationControlPlaneAccessStatus.AUTHORIZED,
                        accessRequest.actorId(),
                        accessRequest.environment(),
                        accessRequest.capability(),
                        AgentEvaluationControlPlaneAccessReason.AUTHORIZED);
            }
        } catch (RuntimeException ignored) {
            // Fail closed and keep provider errors out of the application contract.
        }
        return denied(accessRequest, AgentEvaluationControlPlaneAccessReason.AUTHORIZER_DENIED);
    }

    private static AgentEvaluationControlPlaneAccessDecision denied(
            AgentEvaluationControlPlaneAccessRequest request,
            AgentEvaluationControlPlaneAccessReason reason) {
        return new AgentEvaluationControlPlaneAccessDecision(
                AgentEvaluationControlPlaneAccessStatus.DENIED,
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
