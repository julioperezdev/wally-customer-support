package com.wally.customersupport.agent.application.service;

/** Sanitized application error for comparisons across different datasets. */
public class IncompatibleEvaluationDatasetException extends RuntimeException {

    public IncompatibleEvaluationDatasetException() {
        super("evaluation runs must use the same dataset");
    }
}
