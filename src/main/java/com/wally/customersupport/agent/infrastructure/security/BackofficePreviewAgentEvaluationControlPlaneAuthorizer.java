package com.wally.customersupport.agent.infrastructure.security;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessRequest;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationControlPlaneAuthorizer;

/** Uses the authenticated preview principal for the temporary read-only backoffice mode. */
public final class BackofficePreviewAgentEvaluationControlPlaneAuthorizer
        implements AgentEvaluationControlPlaneAuthorizer {

    @Override
    public boolean authorize(AgentEvaluationControlPlaneAccessRequest request) {
        return JwtAgentEvaluationSecuritySupport.authorizeAuthenticatedToken(
                request.actorId(), request.capability());
    }
}
