package com.wally.customersupport.agent.application.shadow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic, fail-closed gate for deciding whether shadow evidence is
 * sufficient for human review. It has no activation or publication authority.
 */
public final class AgentShadowQualityGate {

    public AgentShadowQualityScorecard evaluate(
            AgentShadowQualityMetrics metrics,
            AgentShadowQualityGatePolicy policy) {
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(policy, "policy");

        List<String> reasons = new ArrayList<>();
        if (!countersAreConsistent(metrics)) {
            reasons.add("INVALID_COUNTERS");
            return scorecard(AgentShadowQualityGateDecision.BLOCK, reasons, metrics, policy);
        }
        if (metrics.totalExecutions() < policy.minimumSampleSize()) {
            reasons.add("SAMPLE_BELOW_MINIMUM");
            return scorecard(AgentShadowQualityGateDecision.INSUFFICIENT_EVIDENCE, reasons, metrics, policy);
        }
        if (metrics.latencyP95Ms() == null || metrics.averageCostUsd() == null) {
            reasons.add("OPERATIONAL_METRICS_UNKNOWN");
            return scorecard(AgentShadowQualityGateDecision.INSUFFICIENT_EVIDENCE, reasons, metrics, policy);
        }

        addIfOver(reasons, "FAILURE_RATE_ABOVE_LIMIT", failureRate(metrics), policy.maximumFailureRate());
        addIfOver(reasons, "MISMATCH_RATE_ABOVE_LIMIT", mismatchRate(metrics), policy.maximumMismatchRate());
        addIfOver(reasons, "UNKNOWN_RATE_ABOVE_LIMIT", unknownRate(metrics), policy.maximumUnknownRate());
        if (metrics.latencyP95Ms() > policy.maximumLatencyP95Ms()) {
            reasons.add("LATENCY_P95_ABOVE_LIMIT");
        }
        if (metrics.averageCostUsd().compareTo(policy.maximumAverageCostUsd()) > 0) {
            reasons.add("AVERAGE_COST_ABOVE_LIMIT");
        }
        return scorecard(
                reasons.isEmpty()
                        ? AgentShadowQualityGateDecision.APPROVE_FOR_REVIEW
                        : AgentShadowQualityGateDecision.BLOCK,
                reasons,
                metrics,
                policy);
    }

    private static boolean countersAreConsistent(AgentShadowQualityMetrics metrics) {
        return metrics.completedExecutions() + metrics.failedExecutions() == metrics.totalExecutions()
                && metrics.matchCount() + metrics.mismatchCount() + metrics.unknownCount()
                        == metrics.totalExecutions();
    }

    private static double failureRate(AgentShadowQualityMetrics metrics) {
        return ratio(metrics.failedExecutions(), metrics.totalExecutions());
    }

    private static double mismatchRate(AgentShadowQualityMetrics metrics) {
        return ratio(metrics.mismatchCount(), metrics.totalExecutions());
    }

    private static double unknownRate(AgentShadowQualityMetrics metrics) {
        return ratio(metrics.unknownCount(), metrics.totalExecutions());
    }

    private static double ratio(int numerator, int denominator) {
        return denominator == 0 ? 1.0 : (double) numerator / denominator;
    }

    private static void addIfOver(List<String> reasons, String reason, double actual, double maximum) {
        if (actual > maximum) {
            reasons.add(reason);
        }
    }

    private static AgentShadowQualityScorecard scorecard(
            AgentShadowQualityGateDecision decision,
            List<String> reasons,
            AgentShadowQualityMetrics metrics,
            AgentShadowQualityGatePolicy policy) {
        return new AgentShadowQualityScorecard(decision, reasons, metrics, policy);
    }
}
