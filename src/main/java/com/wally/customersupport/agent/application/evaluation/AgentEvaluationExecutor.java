package com.wally.customersupport.agent.application.evaluation;

import com.wally.customersupport.agent.domain.model.AgentEvaluationExecution;
import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;

/** Executes one evaluation scenario without exposing response content to the runner. */
@FunctionalInterface
public interface AgentEvaluationExecutor {

    AgentEvaluationExecution execute(AgentEvaluationScenario scenario);
}
