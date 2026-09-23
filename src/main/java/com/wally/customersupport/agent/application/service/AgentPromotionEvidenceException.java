package com.wally.customersupport.agent.application.service;

/** Safe error codes for a lifecycle transition whose evaluation evidence is unusable. */
public class AgentPromotionEvidenceException extends RuntimeException {

    public enum Reason {
        REQUIRED,
        RUN_NOT_FOUND,
        CANDIDATE_VERSION_MISMATCH,
        BASELINE_NOT_ACTIVE,
        DATASET_MISMATCH,
        RUNS_NOT_COMPARABLE
    }

    private final Reason reason;

    public AgentPromotionEvidenceException(Reason reason) {
        super("agent promotion evaluation evidence is invalid: " + reason);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
