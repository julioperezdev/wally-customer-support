package com.wally.customersupport.agent.application.port.out;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationControlPlaneAccessRequest;

/** Provider-neutral authorization boundary for the evaluation control plane. */
@FunctionalInterface
public interface AgentEvaluationControlPlaneAuthorizer {

    boolean authorize(AgentEvaluationControlPlaneAccessRequest request);
}
