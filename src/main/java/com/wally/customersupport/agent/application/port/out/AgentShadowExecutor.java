package com.wally.customersupport.agent.application.port.out;

import com.wally.customersupport.agent.application.shadow.AgentShadowExecutionRequest;
import com.wally.customersupport.agent.application.shadow.AgentShadowExecutionResult;

/** Executes a candidate without granting it any authority to publish a reply. */
public interface AgentShadowExecutor {

    AgentShadowExecutionResult execute(AgentShadowExecutionRequest request);
}
