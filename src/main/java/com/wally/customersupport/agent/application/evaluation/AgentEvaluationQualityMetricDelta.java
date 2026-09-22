package com.wally.customersupport.agent.application.evaluation;

/** Quality-only deltas; unavailable dimensions remain null instead of becoming zero. */
public record AgentEvaluationQualityMetricDelta(
        double passRateDelta,
        double responseValidityRateDelta,
        double responseGroundingRateDelta,
        double safetyRateDelta,
        double utilityRateDelta,
        Double intentAccuracyRateDelta,
        Double entityExtractionRateDelta,
        Double toolSuccessRateDelta,
        Double ragGroundingRateDelta) {

    public AgentEvaluationQualityMetricDelta {
        validateDelta(passRateDelta, "passRateDelta");
        validateDelta(responseValidityRateDelta, "responseValidityRateDelta");
        validateDelta(responseGroundingRateDelta, "responseGroundingRateDelta");
        validateDelta(safetyRateDelta, "safetyRateDelta");
        validateDelta(utilityRateDelta, "utilityRateDelta");
        validateOptionalDelta(intentAccuracyRateDelta, "intentAccuracyRateDelta");
        validateOptionalDelta(entityExtractionRateDelta, "entityExtractionRateDelta");
        validateOptionalDelta(toolSuccessRateDelta, "toolSuccessRateDelta");
        validateOptionalDelta(ragGroundingRateDelta, "ragGroundingRateDelta");
    }

    private static void validateOptionalDelta(Double value, String field) {
        if (value != null) {
            validateDelta(value, field);
        }
    }

    private static void validateDelta(double value, String field) {
        if (!Double.isFinite(value) || value < -1 || value > 1) {
            throw new IllegalArgumentException(field + " must be between -1 and 1");
        }
    }
}
