package com.wally.customersupport.agent.application.port.out;

import com.wally.customersupport.agent.application.shadow.AgentTrafficComparisonEvent;

/** Publishes sanitized candidate-versus-active evidence. */
public interface AgentTrafficComparisonEventPublisher {

    void publish(AgentTrafficComparisonEvent event);
}
