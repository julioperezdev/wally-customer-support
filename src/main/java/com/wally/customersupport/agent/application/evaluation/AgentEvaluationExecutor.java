package com.wally.customersupport.agent.application.evaluation;

import java.util.List;

import com.wally.customersupport.agent.domain.model.AgentEvaluationExecution;
import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;

/** Executes one evaluation scenario without exposing response content to the runner. */
@FunctionalInterface
public interface AgentEvaluationExecutor {

    AgentEvaluationExecution execute(AgentEvaluationScenario scenario);

    /** Resolves immutable, server-owned execution metadata before any Bedrock call. */
    default AgentEvaluationRunRequest prepare(AgentEvaluationRunRequest request) {
        return request;
    }

    /** Validates the run-level provider contract before any scenario is executed. */
    default void validate(AgentEvaluationRunRequest request) {
        // Deterministic executors do not need provider validation.
    }

    /** Validates the complete synthetic suite before a provider-backed run can incur cost. */
    default void validateScenarios(
            List<AgentEvaluationScenario> scenarios,
            AgentEvaluationRunRequest request) {
        // Most deterministic executors have no provider-side scenario limits.
    }

    /** Allows provider-backed executors to receive the immutable run identity. */
    default AgentEvaluationExecution execute(
            AgentEvaluationScenario scenario,
            AgentEvaluationRunRequest request) {
        return execute(scenario);
    }
}
