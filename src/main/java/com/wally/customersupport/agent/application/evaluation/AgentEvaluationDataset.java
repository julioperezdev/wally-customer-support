package com.wally.customersupport.agent.application.evaluation;

import java.util.List;

import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;

/** Versioned synthetic scenarios available to the evaluation application. */
public interface AgentEvaluationDataset {

    String version();

    List<AgentEvaluationScenario> scenarios();
}
