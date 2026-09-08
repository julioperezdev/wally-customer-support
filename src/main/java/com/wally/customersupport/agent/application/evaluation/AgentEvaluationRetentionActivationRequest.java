package com.wally.customersupport.agent.application.evaluation;

import java.time.Instant;

/** Explicit operator evidence used to evaluate the retention activation gate. */
public record AgentEvaluationRetentionActivationRequest(
        boolean requested,
        String environment,
        String approvedBy,
        String approvalReference,
        Instant approvedAt) {

    public AgentEvaluationRetentionActivationRequest {
        environment = optionalText(environment);
        approvedBy = optionalText(approvedBy);
        approvalReference = optionalText(approvalReference);
    }

    public static AgentEvaluationRetentionActivationRequest notRequested() {
        return new AgentEvaluationRetentionActivationRequest(false, null, null, null, null);
    }

    private static String optionalText(String value) {
        return value == null ? null : value.strip();
    }
}
