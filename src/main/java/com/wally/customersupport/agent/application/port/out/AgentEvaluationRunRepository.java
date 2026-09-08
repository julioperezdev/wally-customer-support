package com.wally.customersupport.agent.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRun;

/** Persistence boundary for sanitized, completed evaluation runs. */
public interface AgentEvaluationRunRepository {

    AgentEvaluationRun save(AgentEvaluationRun run);

    Optional<AgentEvaluationRun> findById(UUID runId);
}
