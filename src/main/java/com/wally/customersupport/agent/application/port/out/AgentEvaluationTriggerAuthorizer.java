package com.wally.customersupport.agent.application.port.out;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationTriggerRequest;

/** Provider-neutral authorization boundary for an internal evaluation trigger. */
@FunctionalInterface
public interface AgentEvaluationTriggerAuthorizer {

    boolean authorize(AgentEvaluationTriggerRequest request);
}
