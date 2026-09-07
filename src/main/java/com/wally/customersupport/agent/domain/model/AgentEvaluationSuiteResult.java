package com.wally.customersupport.agent.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Aggregated, sanitized result of one versioned evaluation suite. */
public record AgentEvaluationSuiteResult(
        String datasetVersion,
        List<AgentEvaluationResult> scenarioResults,
        int totalScenarios,
        int passedScenarios,
        int failedScenarios,
        double passRate,
        double averageScore,
        Map<String, Integer> failureReasons) {

    public AgentEvaluationSuiteResult {
        datasetVersion = required(datasetVersion, "datasetVersion");
        scenarioResults = scenarioResults == null ? List.of() : scenarioResults.stream()
                .map(result -> Objects.requireNonNull(result, "scenarioResults must not contain null"))
                .toList();
        if (scenarioResults.isEmpty()) {
            throw new IllegalArgumentException("scenarioResults must not be empty");
        }
        if (totalScenarios != scenarioResults.size()) {
            throw new IllegalArgumentException("totalScenarios must match scenarioResults");
        }
        long calculatedPassed = scenarioResults.stream().filter(AgentEvaluationResult::passed).count();
        if (passedScenarios != calculatedPassed) {
            throw new IllegalArgumentException("passedScenarios must match scenarioResults");
        }
        if (failedScenarios != totalScenarios - passedScenarios) {
            throw new IllegalArgumentException("failedScenarios must match scenarioResults");
        }
        if (Double.isNaN(passRate) || passRate < 0 || passRate > 1) {
            throw new IllegalArgumentException("passRate must be between 0 and 1");
        }
        if (Double.isNaN(averageScore) || averageScore < 0 || averageScore > 1) {
            throw new IllegalArgumentException("averageScore must be between 0 and 1");
        }
        double expectedPassRate = (double) passedScenarios / totalScenarios;
        double expectedAverageScore = scenarioResults.stream()
                .mapToDouble(AgentEvaluationResult::score)
                .average()
                .orElseThrow();
        if (Math.abs(passRate - expectedPassRate) > 0.000001
                || Math.abs(averageScore - expectedAverageScore) > 0.000001) {
            throw new IllegalArgumentException("aggregated metrics must match scenarioResults");
        }
        failureReasons = normalizeReasons(failureReasons);
    }

    private static Map<String, Integer> normalizeReasons(Map<String, Integer> reasons) {
        if (reasons == null) {
            return Map.of();
        }
        Map<String, Integer> normalized = new LinkedHashMap<>();
        reasons.forEach((reason, count) -> {
            String key = required(reason, "failure reason");
            if (count == null || count < 1) {
                throw new IllegalArgumentException("failure reason count must be positive");
            }
            normalized.put(key, count);
        });
        return Map.copyOf(normalized);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
