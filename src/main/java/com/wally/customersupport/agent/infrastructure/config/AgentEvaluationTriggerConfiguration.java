package com.wally.customersupport.agent.infrastructure.config;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationApplicationService;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationTriggerAuthorizer;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationTriggerExecutionGuard;
import com.wally.customersupport.agent.application.service.AgentEvaluationTriggerAuthorizationService;
import com.wally.customersupport.agent.application.service.AgentEvaluationTriggerExecutionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring composition for the internal evaluation trigger boundary. */
@Configuration(proxyBeanMethods = false)
public class AgentEvaluationTriggerConfiguration {

    @Bean
    @ConditionalOnMissingBean(AgentEvaluationTriggerAuthorizer.class)
    AgentEvaluationTriggerAuthorizer denyByDefaultAgentEvaluationTriggerAuthorizer() {
        return request -> false;
    }

    @Bean
    AgentEvaluationTriggerAuthorizationService agentEvaluationTriggerAuthorizationService(
            @Value("${wcs.agent-evaluation.authorization.allowed-environment:prod}") String allowedEnvironment,
            AgentEvaluationTriggerAuthorizer authorizer) {
        return new AgentEvaluationTriggerAuthorizationService(allowedEnvironment, authorizer);
    }

    @Bean
    AgentEvaluationTriggerExecutionService agentEvaluationTriggerExecutionService(
            AgentEvaluationTriggerAuthorizationService authorizationService,
            AgentEvaluationApplicationService evaluationService,
            AgentEvaluationTriggerExecutionGuard executionGuard) {
        return new AgentEvaluationTriggerExecutionService(
                authorizationService,
                evaluationService,
                executionGuard);
    }
}
