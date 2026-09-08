package com.wally.customersupport.agent.application.service;

import java.time.Instant;
import java.util.Objects;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPage;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionDecision;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionPolicy;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRetentionReview;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Reviews one bounded history page without writing, deleting or archiving data. */
@Service
@RequiredArgsConstructor
public class AgentEvaluationRetentionReviewService {

    private final AgentEvaluationRetentionPolicyService policyService;

    public AgentEvaluationRetentionReview review(
            AgentEvaluationHistoryPage page,
            AgentEvaluationRetentionPolicy policy,
            Instant evaluatedAt) {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(policy, "policy");
        Instant reviewInstant = Objects.requireNonNull(evaluatedAt, "evaluatedAt");
        var decisions = page.items().stream()
                .map(run -> policyService.evaluate(run, policy, reviewInstant))
                .toList();
        int activeCount = (int) decisions.stream()
                .filter(decision -> !decision.expired())
                .count();
        int expiredCount = decisions.size() - activeCount;
        return new AgentEvaluationRetentionReview(
                reviewInstant, policy, decisions, activeCount, expiredCount);
    }
}
