package com.wally.customersupport.agent.domain.model;

import java.util.List;

/** Stable names shared by scenario expectations, execution signals and scorecards. */
public final class AgentEvaluationQualityDimensions {

    public static final List<String> ALL = List.of(
            "intent_accuracy",
            "entity_extraction",
            "tool_success",
            "rag_grounding");

    private AgentEvaluationQualityDimensions() {
    }
}
