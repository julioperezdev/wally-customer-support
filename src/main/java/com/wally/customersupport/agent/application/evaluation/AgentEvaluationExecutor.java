package com.wally.customersupport.agent.application.evaluation;

import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;

/** Executes one evaluation scenario without exposing response content to the runner. */
@FunctionalInterface
public interface AgentEvaluationExecutor {

    ResponseHumanizationResult execute(AgentEvaluationScenario scenario);
}
