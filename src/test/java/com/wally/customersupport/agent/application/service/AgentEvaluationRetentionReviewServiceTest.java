package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPage;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPageRequest;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionPolicy;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionStatus;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRunSummary;
import org.junit.jupiter.api.Test;

class AgentEvaluationRetentionReviewServiceTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-09-08T00:00:00Z");
    private static final UUID ACTIVE_RUN = UUID.fromString("00000000-0000-0000-0000-000000000031");
    private static final UUID EXPIRED_RUN = UUID.fromString("00000000-0000-0000-0000-000000000032");

    @Test
    void reviewsPageInStableOrderAndAggregatesStatuses() {
        var service = new AgentEvaluationRetentionReviewService(
                new AgentEvaluationRetentionPolicyService());
        var policy = new AgentEvaluationRetentionPolicy(Duration.ofDays(30));
        var page = page(List.of(
                summary(ACTIVE_RUN, EVALUATED_AT.minus(Duration.ofDays(29))),
                summary(EXPIRED_RUN, EVALUATED_AT.minus(Duration.ofDays(31)))));

        var review = service.review(page, policy, EVALUATED_AT);

        assertThat(review.evaluatedAt()).isEqualTo(EVALUATED_AT);
        assertThat(review.policy()).isEqualTo(policy);
        assertThat(review.decisions()).extracting("runId")
                .containsExactly(ACTIVE_RUN, EXPIRED_RUN);
        assertThat(review.decisions()).extracting("status")
                .containsExactly(AgentEvaluationRetentionStatus.ACTIVE, AgentEvaluationRetentionStatus.EXPIRED);
        assertThat(review.activeCount()).isEqualTo(1);
        assertThat(review.expiredCount()).isEqualTo(1);
        assertThat(review.totalRuns()).isEqualTo(2);
    }

    @Test
    void reviewsEmptyPageWithoutCreatingRetentionActions() {
        var service = new AgentEvaluationRetentionReviewService(
                new AgentEvaluationRetentionPolicyService());
        var page = new AgentEvaluationHistoryPage(
                List.of(), 0, AgentEvaluationHistoryPageRequest.DEFAULT_PAGE_SIZE, 0, 0);

        var review = service.review(page, AgentEvaluationRetentionPolicy.recommended(), EVALUATED_AT);

        assertThat(review.decisions()).isEmpty();
        assertThat(review.activeCount()).isZero();
        assertThat(review.expiredCount()).isZero();
        assertThat(review.totalRuns()).isZero();
    }

    private static AgentEvaluationHistoryPage page(List<AgentEvaluationRunSummary> items) {
        return new AgentEvaluationHistoryPage(
                items, 0, AgentEvaluationHistoryPageRequest.DEFAULT_PAGE_SIZE, items.size(), 1);
    }

    private static AgentEvaluationRunSummary summary(UUID runId, Instant completedAt) {
        return new AgentEvaluationRunSummary(
                runId,
                "catalog-response-v1",
                "catalog-specialist",
                "v1",
                "mock",
                "deterministic-v1",
                completedAt.minusSeconds(1),
                completedAt,
                1,
                1,
                1,
                0,
                1.0,
                1.0,
                Map.of());
    }
}
