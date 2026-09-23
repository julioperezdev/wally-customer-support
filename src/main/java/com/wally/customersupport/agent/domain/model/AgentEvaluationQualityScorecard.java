package com.wally.customersupport.agent.domain.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Sanitized quality dimensions for one evaluation suite.
 *
 * <p>Dimensions are calculated only when every evaluated scenario provides the
 * corresponding execution signal. Missing instrumentation is reported as
 * unavailable instead of being confused with a score of zero.</p>
 */
public record AgentEvaluationQualityScorecard(
        int evaluatedScenarios,
        Double responseValidityRate,
        Double responseGroundingRate,
        Double safetyRate,
        Double utilityRate,
        Double intentAccuracyRate,
        Double entityExtractionRate,
        Double actionAccuracyRate,
        Double toolSuccessRate,
        Double ragGroundingRate,
        Map<String, Integer> failureCounts,
        List<String> unavailableDimensions) {

    public static final List<String> QUALITY_DIMENSIONS = AgentEvaluationQualityDimensions.ALL;

    /** Retained as a compatibility alias for callers that display the old contract. */
    public static final List<String> CURRENTLY_UNAVAILABLE_DIMENSIONS = QUALITY_DIMENSIONS;

    /** Backwards-compatible scorecard without execution quality signals. */
    public AgentEvaluationQualityScorecard(
            int evaluatedScenarios,
            double responseValidityRate,
            double responseGroundingRate,
            double safetyRate,
            double utilityRate,
            Map<String, Integer> failureCounts,
            List<String> unavailableDimensions) {
        this(evaluatedScenarios, responseValidityRate, responseGroundingRate, safetyRate, utilityRate,
                null, null, null, null, null, failureCounts, unavailableDimensions);
    }

    /** Backwards-compatible scorecard before action accuracy was added. */
    public AgentEvaluationQualityScorecard(
            int evaluatedScenarios,
            double responseValidityRate,
            double responseGroundingRate,
            double safetyRate,
            double utilityRate,
            Double intentAccuracyRate,
            Double entityExtractionRate,
            Double toolSuccessRate,
            Double ragGroundingRate,
            Map<String, Integer> failureCounts,
            List<String> unavailableDimensions) {
        this(evaluatedScenarios, responseValidityRate, responseGroundingRate, safetyRate, utilityRate,
                intentAccuracyRate, entityExtractionRate, null, toolSuccessRate, ragGroundingRate,
                failureCounts, unavailableDimensions);
    }

    public AgentEvaluationQualityScorecard {
        if (evaluatedScenarios < 1) {
            throw new IllegalArgumentException("evaluatedScenarios must be positive");
        }
        validateOptionalRate(responseValidityRate, "responseValidityRate");
        validateOptionalRate(responseGroundingRate, "responseGroundingRate");
        validateOptionalRate(safetyRate, "safetyRate");
        validateOptionalRate(utilityRate, "utilityRate");
        validateOptionalRate(intentAccuracyRate, "intentAccuracyRate");
        validateOptionalRate(entityExtractionRate, "entityExtractionRate");
        validateOptionalRate(actionAccuracyRate, "actionAccuracyRate");
        validateOptionalRate(toolSuccessRate, "toolSuccessRate");
        validateOptionalRate(ragGroundingRate, "ragGroundingRate");
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
        List<AgentEvaluationResult> responseResults = safeResults.stream()
                .filter(result -> !isRouterResult(result))
                .toList();
        long valid = responseResults.stream().filter(AgentEvaluationQualityScorecard::hasValidResponse).count();
        long grounded = responseResults.stream().filter(AgentEvaluationQualityScorecard::isGrounded).count();
        long safe = responseResults.stream().filter(AgentEvaluationQualityScorecard::isSafe).count();
        List<AgentEvaluationResult> intentResults = eligible(safeResults, "intent_accuracy");
        List<AgentEvaluationResult> entityResults = eligible(safeResults, "entity_extraction");
        List<AgentEvaluationResult> actionResults = eligible(safeResults, "action_accuracy");
        List<AgentEvaluationResult> toolResults = eligible(safeResults, "tool_success");
        List<AgentEvaluationResult> ragResults = eligible(safeResults, "rag_grounding");
        boolean intentAvailable = hasIntentSignal(intentResults);
        boolean entitiesAvailable = hasEntitySignal(entityResults);
        boolean actionsAvailable = hasActionSignal(actionResults);
        boolean toolAvailable = hasToolSignal(toolResults);
        boolean ragAvailable = hasRagSignal(ragResults);
        List<String> unavailable = new java.util.ArrayList<>(QUALITY_DIMENSIONS.stream()
                .filter(dimension -> !isAvailable(dimension, intentAvailable, entitiesAvailable,
                        actionsAvailable, toolAvailable, ragAvailable))
                .toList());
        if (responseResults.isEmpty()) {
            unavailable.addAll(List.of("response_validity", "response_grounding", "safety", "utility"));
        }
        return new AgentEvaluationQualityScorecard(
                total,
                responseResults.isEmpty() ? null : rate(valid, responseResults.size()),
                responseResults.isEmpty() ? null : rate(grounded, responseResults.size()),
                responseResults.isEmpty() ? null : rate(safe, responseResults.size()),
                responseResults.isEmpty() ? null : responseResults.stream().mapToDouble(AgentEvaluationResult::score).average().orElse(averageScore),
                intentAvailable ? rate(successes(intentResults, "INTENT_MISMATCH"), intentResults.size()) : null,
                entitiesAvailable ? rate(successes(entityResults, "ENTITY_EXTRACTION_MISMATCH"), entityResults.size()) : null,
                actionsAvailable ? rate(successes(actionResults, "ACTION_MISMATCH"), actionResults.size()) : null,
                toolAvailable ? rate(successes(toolResults, "TOOL_SUCCESS_MISMATCH"), toolResults.size()) : null,
                ragAvailable ? rate(successes(ragResults, "RAG_GROUNDING_MISMATCH"), ragResults.size()) : null,
                failureCounts(safeResults),
                unavailable);
    }

    private static boolean isRouterResult(AgentEvaluationResult result) {
        return result.evaluatedDimensions().contains("action_accuracy")
                && result.evaluatedDimensions().contains("intent_accuracy")
                && result.evaluatedDimensions().contains("entity_extraction");
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

    private static long successes(List<AgentEvaluationResult> results, String failureReason) {
        return results.stream().filter(result -> !result.reasons().contains(failureReason)).count();
    }

    private static List<AgentEvaluationResult> eligible(
            List<AgentEvaluationResult> results,
            String dimension) {
        return results.stream()
                .filter(result -> result.evaluatedDimensions().contains(dimension))
                .toList();
    }

    private static boolean hasIntentSignal(List<AgentEvaluationResult> results) {
        return !results.isEmpty() && results.stream().allMatch(result -> result.executionMetadata() != null
                && result.executionMetadata().routedIntent() != null);
    }

    private static boolean hasEntitySignal(List<AgentEvaluationResult> results) {
        return !results.isEmpty() && results.stream().allMatch(result -> result.executionMetadata() != null
                && result.executionMetadata().resolvedEntityTypes() != null);
    }

    private static boolean hasActionSignal(List<AgentEvaluationResult> results) {
        return !results.isEmpty() && results.stream().allMatch(result -> result.executionMetadata() != null
                && result.executionMetadata().routedAction() != null);
    }

    private static boolean hasToolSignal(List<AgentEvaluationResult> results) {
        return !results.isEmpty() && results.stream().allMatch(result -> result.executionMetadata() != null
                && result.executionMetadata().toolName() != null
                && result.executionMetadata().toolSucceeded() != null);
    }

    private static boolean hasRagSignal(List<AgentEvaluationResult> results) {
        return !results.isEmpty() && results.stream().allMatch(result -> result.executionMetadata() != null
                && result.executionMetadata().grounded() != null);
    }

    private static boolean isAvailable(
            String dimension,
            boolean intentAvailable,
            boolean entitiesAvailable,
            boolean actionsAvailable,
            boolean toolAvailable,
            boolean ragAvailable) {
        return switch (dimension) {
            case "intent_accuracy" -> intentAvailable;
            case "entity_extraction" -> entitiesAvailable;
            case "action_accuracy" -> actionsAvailable;
            case "tool_success" -> toolAvailable;
            case "rag_grounding" -> ragAvailable;
            default -> false;
        };
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

    private static void validateOptionalRate(Double value, String field) {
        if (value != null) {
            validateRate(value, field);
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
