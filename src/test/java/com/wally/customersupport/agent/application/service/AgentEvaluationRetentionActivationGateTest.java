package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionActivationReason;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionActivationRequest;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionActivationStatus;
import org.junit.jupiter.api.Test;

class AgentEvaluationRetentionActivationGateTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-09-08T00:00:00Z");

    @Test
    void returnsNotRequestedWithoutApprovalMetadata() {
        var decision = gate().evaluate(
                AgentEvaluationRetentionActivationRequest.notRequested(), EVALUATED_AT);

        assertThat(decision.status())
                .isEqualTo(AgentEvaluationRetentionActivationStatus.NOT_REQUESTED);
        assertThat(decision.reason()).isEqualTo(AgentEvaluationRetentionActivationReason.NONE);
        assertThat(decision.approvedForReview()).isFalse();
    }

    @Test
    void approvesReviewWhenEvidenceIsCompleteAndNotFutureDated() {
        var request = new AgentEvaluationRetentionActivationRequest(
                true, "prod", "retention-reviewer", "change-WCS-67", EVALUATED_AT);

        var decision = gate().evaluate(request, EVALUATED_AT);

        assertThat(decision.status())
                .isEqualTo(AgentEvaluationRetentionActivationStatus.APPROVED_FOR_REVIEW);
        assertThat(decision.reason()).isEqualTo(AgentEvaluationRetentionActivationReason.NONE);
        assertThat(decision.environment()).isEqualTo("prod");
        assertThat(decision.approvedBy()).isEqualTo("retention-reviewer");
        assertThat(decision.approvalReference()).isEqualTo("change-WCS-67");
        assertThat(decision.approvedForReview()).isTrue();
    }

    @Test
    void rejectsMissingApprovalEvidenceWithSanitizedReason() {
        var request = new AgentEvaluationRetentionActivationRequest(
                true, "prod", "", "change-WCS-67", EVALUATED_AT);

        var decision = gate().evaluate(request, EVALUATED_AT);

        assertThat(decision.status()).isEqualTo(AgentEvaluationRetentionActivationStatus.REJECTED);
        assertThat(decision.reason()).isEqualTo(AgentEvaluationRetentionActivationReason.MISSING_APPROVER);
        assertThat(decision.approvedForReview()).isFalse();
    }

    @Test
    void rejectsEveryMissingApprovalField() {
        assertThat(gate().evaluate(
                new AgentEvaluationRetentionActivationRequest(
                        true, "", "retention-reviewer", "change-WCS-67", EVALUATED_AT),
                EVALUATED_AT).reason())
                .isEqualTo(AgentEvaluationRetentionActivationReason.MISSING_ENVIRONMENT);
        assertThat(gate().evaluate(
                new AgentEvaluationRetentionActivationRequest(
                        true, "prod", "retention-reviewer", "", EVALUATED_AT),
                EVALUATED_AT).reason())
                .isEqualTo(AgentEvaluationRetentionActivationReason.MISSING_APPROVAL_REFERENCE);
        assertThat(gate().evaluate(
                new AgentEvaluationRetentionActivationRequest(
                        true, "prod", "retention-reviewer", "change-WCS-67", null),
                EVALUATED_AT).reason())
                .isEqualTo(AgentEvaluationRetentionActivationReason.MISSING_APPROVED_AT);
    }

    @Test
    void rejectsApprovalFromTheFuture() {
        var request = new AgentEvaluationRetentionActivationRequest(
                true, "prod", "retention-reviewer", "change-WCS-67", EVALUATED_AT.plusSeconds(1));

        var decision = gate().evaluate(request, EVALUATED_AT);

        assertThat(decision.status()).isEqualTo(AgentEvaluationRetentionActivationStatus.REJECTED);
        assertThat(decision.reason()).isEqualTo(AgentEvaluationRetentionActivationReason.APPROVAL_IN_FUTURE);
    }

    private static AgentEvaluationRetentionActivationGate gate() {
        return new AgentEvaluationRetentionActivationGate();
    }
}
