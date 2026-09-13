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
    public static final String REGISTRY_WRITE_CAPABILITY = "agent-registry.write";
    public static final String FEATURE_FLAGS_READ_CAPABILITY = "feature-flags.read";
    public static final String FEATURE_FLAGS_WRITE_CAPABILITY = "feature-flags.write";
    public static final String BACKOFFICE_CATALOG_READ_CAPABILITY = "backoffice.catalog.read";
    public static final String BACKOFFICE_CATALOG_WRITE_CAPABILITY = "backoffice.catalog.write";
    public static final String BACKOFFICE_CATALOG_MEDIA_WRITE_CAPABILITY = "backoffice.catalog.media.write";
    public static final String BACKOFFICE_HUMAN_FOLLOW_UP_READ_CAPABILITY = "backoffice.human-follow-up.read";
    public static final String BACKOFFICE_HUMAN_FOLLOW_UP_WRITE_CAPABILITY = "backoffice.human-follow-up.write";
    public static final String BACKOFFICE_ORDERS_READ_CAPABILITY = "backoffice.orders.read";
    public static final String BACKOFFICE_ORDERS_WRITE_CAPABILITY = "backoffice.orders.write";

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

    /** Builds a read-only registry request for the explicitly requested environment. */
    public AgentEvaluationControlPlaneAccessDecision authorizeRegistry(
            String actorId,
            String requestedEnvironment) {
        return authorize(new AgentEvaluationControlPlaneAccessRequest(
                actorId,
                requestedEnvironment,
                REGISTRY_READ_CAPABILITY));
    }

    /** Builds the internal registry mutation request without accepting an environment from HTTP callers. */
    public AgentEvaluationControlPlaneAccessDecision authorizeRegistryWrite(String actorId) {
        return authorize(new AgentEvaluationControlPlaneAccessRequest(
                actorId,
                allowedEnvironment,
                REGISTRY_WRITE_CAPABILITY));
    }

    public AgentEvaluationControlPlaneAccessDecision authorizeRegistryWrite(
            String actorId,
            String requestedEnvironment) {
        return authorize(new AgentEvaluationControlPlaneAccessRequest(
                actorId,
                requestedEnvironment,
                REGISTRY_WRITE_CAPABILITY));
    }

    public AgentEvaluationControlPlaneAccessDecision authorizeFeatureFlags(String actorId) {
        return authorize(new AgentEvaluationControlPlaneAccessRequest(
                actorId, allowedEnvironment, FEATURE_FLAGS_READ_CAPABILITY));
    }

    public AgentEvaluationControlPlaneAccessDecision authorizeFeatureFlagsWrite(String actorId) {
        return authorize(new AgentEvaluationControlPlaneAccessRequest(
                actorId, allowedEnvironment, FEATURE_FLAGS_WRITE_CAPABILITY));
    }

    public AgentEvaluationControlPlaneAccessDecision authorizeBackoffice(
            String actorId,
            String capability) {
        return authorize(new AgentEvaluationControlPlaneAccessRequest(
                actorId, allowedEnvironment, capability));
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
                && !REGISTRY_READ_CAPABILITY.equals(accessRequest.capability())
                && !REGISTRY_WRITE_CAPABILITY.equals(accessRequest.capability())
                && !FEATURE_FLAGS_READ_CAPABILITY.equals(accessRequest.capability())
                && !FEATURE_FLAGS_WRITE_CAPABILITY.equals(accessRequest.capability())
                && !BACKOFFICE_CATALOG_READ_CAPABILITY.equals(accessRequest.capability())
                && !BACKOFFICE_CATALOG_WRITE_CAPABILITY.equals(accessRequest.capability())
                && !BACKOFFICE_CATALOG_MEDIA_WRITE_CAPABILITY.equals(accessRequest.capability())
                && !BACKOFFICE_HUMAN_FOLLOW_UP_READ_CAPABILITY.equals(accessRequest.capability())
                && !BACKOFFICE_HUMAN_FOLLOW_UP_WRITE_CAPABILITY.equals(accessRequest.capability())
                && !BACKOFFICE_ORDERS_READ_CAPABILITY.equals(accessRequest.capability())
                && !BACKOFFICE_ORDERS_WRITE_CAPABILITY.equals(accessRequest.capability())) {
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
