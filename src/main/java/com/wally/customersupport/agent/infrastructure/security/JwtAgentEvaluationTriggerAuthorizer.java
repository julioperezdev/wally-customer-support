package com.wally.customersupport.agent.infrastructure.security;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerRequest;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationTriggerAuthorizer;
import com.wally.customersupport.agent.application.service.AgentEvaluationTriggerAuthorizationService;

/** Spring Security adapter for the exact evaluation execution capability. */
public class JwtAgentEvaluationTriggerAuthorizer implements AgentEvaluationTriggerAuthorizer {

    @Override
    public boolean authorize(AgentEvaluationTriggerRequest request) {
        return JwtAgentEvaluationSecuritySupport.authorize(
                request.actorId(),
                AgentEvaluationTriggerAuthorizationService.EVALUATION_EXECUTE_CAPABILITY);
    }
}
