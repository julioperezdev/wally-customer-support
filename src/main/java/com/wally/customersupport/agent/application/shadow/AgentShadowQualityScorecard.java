package com.wally.customersupport.agent.application.shadow;

import java.util.List;
import java.util.Objects;

/** Content-free quality decision for human review of a candidate. */
public record AgentShadowQualityScorecard(
        AgentShadowQualityGateDecision decision,
        List<String> reasons,
        AgentShadowQualityMetrics metrics,
        AgentShadowQualityGatePolicy policy) {

    public AgentShadowQualityScorecard {
        decision = Objects.requireNonNull(decision, "decision");
        reasons = reasons == null ? List.of() : reasons.stream()
                .map(reason -> Objects.requireNonNull(reason, "reasons must not contain null"))
                .toList();
        metrics = Objects.requireNonNull(metrics, "metrics");
        policy = Objects.requireNonNull(policy, "policy");
    }
}
