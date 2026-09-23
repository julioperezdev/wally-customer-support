package com.wally.customersupport.agent.application.evaluation;

/** Quality-only deltas; unavailable dimensions remain null instead of becoming zero. */
public record AgentEvaluationQualityMetricDelta(
        double passRateDelta,
        Double responseValidityRateDelta,
        Double responseGroundingRateDelta,
        Double safetyRateDelta,
        Double utilityRateDelta,
        Double intentAccuracyRateDelta,
        Double entityExtractionRateDelta,
        Double actionAccuracyRateDelta,
        Double quantityExtractionRateDelta,
        Double toolSuccessRateDelta,
        Double ragGroundingRateDelta) {

    public AgentEvaluationQualityMetricDelta(
            double passRateDelta,
            double responseValidityRateDelta,
            double responseGroundingRateDelta,
            double safetyRateDelta,
            double utilityRateDelta,
            Double intentAccuracyRateDelta,
            Double entityExtractionRateDelta,
            Double toolSuccessRateDelta,
            Double ragGroundingRateDelta) {
        this(passRateDelta, responseValidityRateDelta, responseGroundingRateDelta, safetyRateDelta,
                utilityRateDelta, intentAccuracyRateDelta, entityExtractionRateDelta, null,
                null, toolSuccessRateDelta, ragGroundingRateDelta);
    }

    /** Backwards-compatible quality delta before quantity extraction was measured. */
    public AgentEvaluationQualityMetricDelta(
            double passRateDelta,
            Double responseValidityRateDelta,
            Double responseGroundingRateDelta,
            Double safetyRateDelta,
            Double utilityRateDelta,
            Double intentAccuracyRateDelta,
            Double entityExtractionRateDelta,
            Double actionAccuracyRateDelta,
            Double toolSuccessRateDelta,
            Double ragGroundingRateDelta) {
        this(passRateDelta, responseValidityRateDelta, responseGroundingRateDelta, safetyRateDelta,
                utilityRateDelta, intentAccuracyRateDelta, entityExtractionRateDelta, actionAccuracyRateDelta,
                null, toolSuccessRateDelta, ragGroundingRateDelta);
    }

    public AgentEvaluationQualityMetricDelta {
        validateDelta(passRateDelta, "passRateDelta");
        validateOptionalDelta(responseValidityRateDelta, "responseValidityRateDelta");
        validateOptionalDelta(responseGroundingRateDelta, "responseGroundingRateDelta");
        validateOptionalDelta(safetyRateDelta, "safetyRateDelta");
        validateOptionalDelta(utilityRateDelta, "utilityRateDelta");
        validateOptionalDelta(intentAccuracyRateDelta, "intentAccuracyRateDelta");
        validateOptionalDelta(entityExtractionRateDelta, "entityExtractionRateDelta");
        validateOptionalDelta(actionAccuracyRateDelta, "actionAccuracyRateDelta");
        validateOptionalDelta(quantityExtractionRateDelta, "quantityExtractionRateDelta");
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
