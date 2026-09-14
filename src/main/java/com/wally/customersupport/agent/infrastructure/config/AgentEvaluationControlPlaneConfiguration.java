package com.wally.customersupport.agent.infrastructure.config;

import com.wally.customersupport.agent.application.port.out.AgentEvaluationControlPlaneAuthorizer;
import com.wally.customersupport.agent.application.service.AgentEvaluationControlPlaneAccessService;
import com.wally.customersupport.agent.domain.model.AgentActivationPolicy;
import com.wally.customersupport.agent.domain.model.AgentLifecyclePolicy;
import com.wally.customersupport.agent.infrastructure.security.JwtAgentEvaluationControlPlaneAuthorizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring composition for the Cognito/JWT evaluation control-plane boundary. */
@Configuration(proxyBeanMethods = false)
public class AgentEvaluationControlPlaneConfiguration {

    @Bean
    AgentEvaluationControlPlaneAuthorizer jwtAgentEvaluationControlPlaneAuthorizer() {
        return new JwtAgentEvaluationControlPlaneAuthorizer();
    }

    @Bean
    AgentEvaluationControlPlaneAccessService agentEvaluationControlPlaneAccessService(
            @Value("${wcs.agent-evaluation.control-plane.allowed-environment:prod}") String allowedEnvironment,
            AgentEvaluationControlPlaneAuthorizer authorizer) {
        return new AgentEvaluationControlPlaneAccessService(allowedEnvironment, authorizer);
    }

    @Bean
    AgentActivationPolicy agentActivationPolicy() {
        return new AgentActivationPolicy();
    }

    @Bean
    AgentLifecyclePolicy agentLifecyclePolicy() {
        return new AgentLifecyclePolicy();
    }
}
