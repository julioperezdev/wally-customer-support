package com.wally.customersupport.agent.application.evaluation;

/** Sanitized application error for an evaluation dataset that is not registered. */
public class UnknownAgentEvaluationDatasetException extends IllegalArgumentException {

    public UnknownAgentEvaluationDatasetException(String version) {
        super("evaluation dataset is not registered: " + version);
    }
}
