package com.wally.customersupport.agent.infrastructure.config;

import com.wally.customersupport.agent.application.port.out.AgentEvaluationControlPlaneAuthorizer;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring composition for the read-only evaluation control-plane boundary. */
@Configuration(proxyBeanMethods = false)
public class AgentEvaluationControlPlaneConfiguration {

    @Bean
    @ConditionalOnMissingBean(AgentEvaluationControlPlaneAuthorizer.class)
    AgentEvaluationControlPlaneAuthorizer denyByDefaultAgentEvaluationControlPlaneAuthorizer() {
        return request -> false;
    }

    @Bean
    AgentEvaluationControlPlaneAccessService agentEvaluationControlPlaneAccessService(
            @Value("${wcs.agent-evaluation.control-plane.allowed-environment:prod}") String allowedEnvironment,
            AgentEvaluationControlPlaneAuthorizer authorizer) {
        return new AgentEvaluationControlPlaneAccessService(allowedEnvironment, authorizer);
    }
}
