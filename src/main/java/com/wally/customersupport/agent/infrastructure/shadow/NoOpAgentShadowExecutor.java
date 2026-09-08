package com.wally.customersupport.agent.infrastructure.shadow;

import com.wally.customersupport.agent.application.port.out.AgentShadowExecutor;
import com.wally.customersupport.agent.application.shadow.AgentShadowExecutionRequest;
import com.wally.customersupport.agent.application.shadow.AgentShadowExecutionResult;
import org.springframework.stereotype.Component;

/** Default closed-world executor; a provider adapter must be explicitly added. */
@Component
public class NoOpAgentShadowExecutor implements AgentShadowExecutor {

    @Override
    public AgentShadowExecutionResult execute(AgentShadowExecutionRequest request) {
        return new AgentShadowExecutionResult(
                "NOT_CONFIGURED", 0, null, null, null, null, "NO_SHADOW_EXECUTOR");
    }
}
