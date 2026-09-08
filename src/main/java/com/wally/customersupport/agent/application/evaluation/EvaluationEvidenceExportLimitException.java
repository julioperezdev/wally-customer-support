package com.wally.customersupport.agent.application.evaluation;

/** Sanitized failure raised when an evidence export exceeds its bounded size. */
public class EvaluationEvidenceExportLimitException extends RuntimeException {

    public EvaluationEvidenceExportLimitException() {
        super("evaluation evidence export exceeds the maximum scenario limit");
    }
}
