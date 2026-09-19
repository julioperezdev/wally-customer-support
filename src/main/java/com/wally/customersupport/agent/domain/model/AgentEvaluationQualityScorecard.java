package com.wally.customersupport.agent.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sanitized quality dimensions for one evaluation suite.
 *
 * <p>Only dimensions supported by the current response-policy evaluator are
 * calculated. Dimensions that need a routing, entity, tool or retrieval
 * oracle remain explicitly unavailable instead of being reported as zero.</p>
 */
public record AgentEvaluationQualityScorecard(
        int evaluatedScenarios,
        double responseValidityRate,
        double responseGroundingRate,
        double safetyRate,
        double utilityRate,
        Map<String, Integer> failureCounts,
        List<String> unavailableDimensions) {

    public static final List<String> CURRENTLY_UNAVAILABLE_DIMENSIONS = List.of(
            "intent_accuracy",
            "entity_extraction",
            "tool_success",
            "rag_grounding");

    public AgentEvaluationQualityScorecard {
        if (evaluatedScenarios < 1) {
            throw new IllegalArgumentException("evaluatedScenarios must be positive");
        }
        validateRate(responseValidityRate, "responseValidityRate");
        validateRate(responseGroundingRate, "responseGroundingRate");
        validateRate(safetyRate, "safetyRate");
        validateRate(utilityRate, "utilityRate");
        failureCounts = normalizeCounts(failureCounts);
        unavailableDimensions = unavailableDimensions == null
                ? List.of()
                : unavailableDimensions.stream()
                        .filter(Objects::nonNull)
                        .map(String::strip)
                        .filter(value -> !value.isBlank())
                        .distinct()
                        .sorted()
                        .toList();
    }

    public static AgentEvaluationQualityScorecard from(AgentEvaluationSuiteResult suiteResult) {
        Objects.requireNonNull(suiteResult, "suiteResult");
        return fromResults(suiteResult.scenarioResults(), suiteResult.averageScore());
    }

    public static AgentEvaluationQualityScorecard fromResults(
            List<AgentEvaluationResult> results,
            double averageScore) {
        List<AgentEvaluationResult> safeResults = results == null ? List.of() : results;
        if (safeResults.isEmpty()) {
            throw new IllegalArgumentException("results must not be empty");
        }
        int total = safeResults.size();
        long valid = safeResults.stream().filter(AgentEvaluationQualityScorecard::hasValidResponse).count();
        long grounded = safeResults.stream().filter(AgentEvaluationQualityScorecard::isGrounded).count();
        long safe = safeResults.stream().filter(AgentEvaluationQualityScorecard::isSafe).count();
        return new AgentEvaluationQualityScorecard(
                total,
                rate(valid, total),
                rate(grounded, total),
                rate(safe, total),
                averageScore,
                failureCounts(safeResults),
                CURRENTLY_UNAVAILABLE_DIMENSIONS);
    }

    private static boolean hasValidResponse(AgentEvaluationResult result) {
        return !result.reasons().contains("RESPONSE_MISSING")
                && !result.reasons().contains("OUTCOME_MISMATCH");
    }

    private static boolean isGrounded(AgentEvaluationResult result) {
        return hasValidResponse(result) && !result.reasons().contains("REQUIRED_TEXT_MISSING");
    }

    private static boolean isSafe(AgentEvaluationResult result) {
        return hasValidResponse(result) && !result.reasons().contains("FORBIDDEN_TEXT_PRESENT");
    }

    private static Map<String, Integer> failureCounts(List<AgentEvaluationResult> results) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        results.forEach(result -> result.reasons().forEach(reason -> counts.merge(reason, 1, Integer::sum)));
        return counts;
    }

    private static double rate(long numerator, int denominator) {
        return (double) numerator / denominator;
    }

    private static void validateRate(double value, String field) {
        if (Double.isNaN(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(field + " must be between 0 and 1");
        }
    }

    private static Map<String, Integer> normalizeCounts(Map<String, Integer> counts) {
        if (counts == null) {
            return Map.of();
        }
        Map<String, Integer> normalized = new LinkedHashMap<>();
        counts.forEach((key, value) -> {
            String reason = Objects.requireNonNull(key, "failure count key").strip();
            if (reason.isBlank() || value == null || value < 1) {
                throw new IllegalArgumentException("failure counts must contain positive values and non-blank keys");
            }
            normalized.put(reason, value);
        });
        return Map.copyOf(normalized);
    }
}
