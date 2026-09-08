package com.wally.customersupport.agent.infrastructure.security;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessRequest;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationControlPlaneAuthorizer;

/** Spring Security adapter that maps a validated JWT to the control-plane port. */
public class JwtAgentEvaluationControlPlaneAuthorizer implements AgentEvaluationControlPlaneAuthorizer {

    @Override
    public boolean authorize(AgentEvaluationControlPlaneAccessRequest request) {
        return JwtAgentEvaluationSecuritySupport.authorize(
                request.actorId(),
                AgentEvaluationControlPlaneAccessService.EVALUATION_READ_CAPABILITY);
    }
}
