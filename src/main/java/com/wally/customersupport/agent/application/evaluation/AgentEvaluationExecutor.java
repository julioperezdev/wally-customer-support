package com.wally.customersupport.agent.application.evaluation;

import com.wally.customersupport.agent.domain.model.AgentEvaluationExecution;
import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;

/** Executes one evaluation scenario without exposing response content to the runner. */
@FunctionalInterface
public interface AgentEvaluationExecutor {

    AgentEvaluationExecution execute(AgentEvaluationScenario scenario);

    /** Validates the run-level provider contract before any scenario is executed. */
    default void validate(AgentEvaluationRunRequest request) {
        // Deterministic executors do not need provider validation.
    }

    /** Allows provider-backed executors to receive the immutable run identity. */
    default AgentEvaluationExecution execute(
            AgentEvaluationScenario scenario,
            AgentEvaluationRunRequest request) {
        return execute(scenario);
    }
}
