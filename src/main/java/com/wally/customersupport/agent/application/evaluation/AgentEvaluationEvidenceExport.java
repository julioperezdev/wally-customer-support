package com.wally.customersupport.agent.application.evaluation;

import java.util.Objects;

/** Versioned, content-free export envelope for evaluation evidence. */
public record AgentEvaluationEvidenceExport(
        String schemaVersion,
        AgentEvaluationComparison comparison) {

    public static final String SCHEMA_VERSION = "wcs.agent-evaluation-evidence.v1";
    public static final int MAX_SCENARIOS = 1_000;

    public AgentEvaluationEvidenceExport {
        schemaVersion = required(schemaVersion, "schemaVersion");
        comparison = Objects.requireNonNull(comparison, "comparison");
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("unsupported evidence export schema version");
        }
        if (comparison.scenarios().size() > MAX_SCENARIOS) {
            throw new EvaluationEvidenceExportLimitException();
        }
    }

    public static AgentEvaluationEvidenceExport from(AgentEvaluationComparison comparison) {
        return new AgentEvaluationEvidenceExport(SCHEMA_VERSION, comparison);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
