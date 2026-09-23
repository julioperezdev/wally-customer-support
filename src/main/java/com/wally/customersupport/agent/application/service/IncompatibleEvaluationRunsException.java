package com.wally.customersupport.agent.application.service;

import java.util.Objects;

/** Sanitized reason why two evaluation runs cannot support a valid comparison. */
public class IncompatibleEvaluationRunsException extends RuntimeException {

    public enum Reason {
        DATASET("INCOMPATIBLE_DATASET", "evaluation runs must use the same dataset"),
        AGENT("INCOMPATIBLE_AGENT", "evaluation runs must use the same logical agent"),
        SCENARIO_COVERAGE("INCOMPLETE_SCENARIO_COVERAGE", "evaluation runs must cover the same scenarios");

        private final String code;
        private final String message;

        Reason(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String code() {
            return code;
        }

        public String message() {
            return message;
        }
    }

    private final Reason reason;

    public IncompatibleEvaluationRunsException(Reason reason) {
        super(Objects.requireNonNull(reason, "reason").message());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
