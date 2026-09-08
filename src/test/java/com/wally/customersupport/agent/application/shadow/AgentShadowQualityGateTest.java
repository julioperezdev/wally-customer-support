package com.wally.customersupport.agent.application.shadow;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class AgentShadowQualityGateTest {

    private final AgentShadowQualityGate gate = new AgentShadowQualityGate();
    private final AgentShadowQualityGatePolicy policy = new AgentShadowQualityGatePolicy(
            20, 0.10, 0.15, 0.10, 5_000, new BigDecimal("0.0100"));

    @Test
    void approvesOnlyEvidenceWithinAllReviewLimits() {
        var scorecard = gate.evaluate(metrics(20, 19, 1, 17, 2, 1, 1_200L, "0.001"), policy);

        assertThat(scorecard.decision()).isEqualTo(AgentShadowQualityGateDecision.APPROVE_FOR_REVIEW);
        assertThat(scorecard.reasons()).isEmpty();
    }

    @Test
    void requiresMinimumEvidenceBeforeReview() {
        var scorecard = gate.evaluate(metrics(19, 19, 0, 18, 1, 0, 1_200L, "0.001"), policy);

        assertThat(scorecard.decision()).isEqualTo(AgentShadowQualityGateDecision.INSUFFICIENT_EVIDENCE);
        assertThat(scorecard.reasons()).containsExactly("SAMPLE_BELOW_MINIMUM");
    }

    @Test
    void blocksQualityAndOperationalRegressions() {
        var scorecard = gate.evaluate(metrics(20, 17, 3, 10, 6, 4, 5_001L, "0.011"), policy);

        assertThat(scorecard.decision()).isEqualTo(AgentShadowQualityGateDecision.BLOCK);
        assertThat(scorecard.reasons()).containsExactlyInAnyOrder(
                "FAILURE_RATE_ABOVE_LIMIT",
                "MISMATCH_RATE_ABOVE_LIMIT",
                "UNKNOWN_RATE_ABOVE_LIMIT",
                "LATENCY_P95_ABOVE_LIMIT",
                "AVERAGE_COST_ABOVE_LIMIT");
    }

    @Test
    void failsClosedWhenCountersAreInconsistent() {
        var scorecard = gate.evaluate(metrics(20, 19, 1, 18, 2, 2, 1_200L, "0.001"), policy);

        assertThat(scorecard.decision()).isEqualTo(AgentShadowQualityGateDecision.BLOCK);
        assertThat(scorecard.reasons()).containsExactly("INVALID_COUNTERS");
    }

    @Test
    void doesNotApproveWhenOperationalEvidenceIsMissing() {
        var scorecard = gate.evaluate(metrics(20, 20, 0, 18, 2, 0, null, null), policy);

        assertThat(scorecard.decision()).isEqualTo(AgentShadowQualityGateDecision.INSUFFICIENT_EVIDENCE);
        assertThat(scorecard.reasons()).containsExactly("OPERATIONAL_METRICS_UNKNOWN");
    }

    private static AgentShadowQualityMetrics metrics(
            int total,
            int completed,
            int failed,
            int matches,
            int mismatches,
            int unknown,
            Long latency,
            String averageCost) {
        return new AgentShadowQualityMetrics(
                total,
                completed,
                failed,
                matches,
                mismatches,
                unknown,
                latency,
                averageCost == null ? null : new BigDecimal(averageCost));
    }
}
