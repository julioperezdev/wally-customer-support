package com.wally.customersupport.agent.application.evaluation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.wally.customersupport.agent.domain.model.AgentEvaluationResult;
import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import com.wally.customersupport.agent.domain.model.AgentEvaluationExecutionMetadata;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import org.springframework.stereotype.Component;

/**
 * Evaluates a response policy without invoking a model or persisting the
 * response. Reasons are stable identifiers so they can be aggregated safely.
 */
@Component
public class ResponsePolicyEvaluator {

    public AgentEvaluationResult evaluate(
            AgentEvaluationScenario scenario,
            ResponseHumanizationResult actual) {
        return evaluate(scenario, actual, null);
    }

    public AgentEvaluationResult evaluate(
            AgentEvaluationScenario scenario,
            ResponseHumanizationResult actual,
            AgentEvaluationExecutionMetadata executionMetadata) {
        if (scenario == null) {
            throw new IllegalArgumentException("scenario must not be null");
        }

        List<String> reasons = new ArrayList<>();
        int checks = 1 + scenario.requiredTextFragments().size()
                + scenario.forbiddenTextFragments().size();
        checks += qualitySignalChecks(scenario);
        int passedChecks = 0;

        if (actual == null) {
            reasons.add("RESPONSE_MISSING");
        } else {
            if (actual.outcome() == scenario.expectedOutcome()) {
                passedChecks++;
            } else {
                reasons.add("OUTCOME_MISMATCH");
            }

            String text = actual.text();
            for (String requiredFragment : scenario.requiredTextFragments()) {
                if (text.contains(requiredFragment)) {
                    passedChecks++;
                } else {
                    reasons.add("REQUIRED_TEXT_MISSING");
                }
            }
            for (String forbiddenFragment : scenario.forbiddenTextFragments()) {
                if (!text.contains(forbiddenFragment)) {
                    passedChecks++;
                } else {
                    reasons.add("FORBIDDEN_TEXT_PRESENT");
                }
            }
            if (executionMetadata != null) {
                passedChecks += evaluateQualitySignals(scenario, executionMetadata, reasons);
            } else {
                addUnavailableQualitySignals(scenario, reasons);
            }
        }

        boolean passed = actual != null && reasons.isEmpty();
        double score = (double) passedChecks / checks;
        return new AgentEvaluationResult(
                scenario.scenarioId(),
                scenario.datasetVersion(),
                passed,
                score,
                reasons,
                executionMetadata,
                evaluatedDimensions(scenario));
    }

    private static List<String> evaluatedDimensions(AgentEvaluationScenario scenario) {
        List<String> dimensions = new ArrayList<>();
        if (scenario.expectedIntent() != null) {
            dimensions.add("intent_accuracy");
        }
        if (scenario.expectedEntityTypes() != null) {
            dimensions.add("entity_extraction");
        }
        if (scenario.expectedToolName() != null) {
            dimensions.add("tool_success");
        }
        if (scenario.expectedGrounded() != null) {
            dimensions.add("rag_grounding");
        }
        return dimensions;
    }

    private static int qualitySignalChecks(AgentEvaluationScenario scenario) {
        int checks = 0;
        if (scenario.expectedIntent() != null) {
            checks++;
        }
        if (scenario.expectedEntityTypes() != null) {
            checks++;
        }
        if (scenario.expectedToolName() != null) {
            checks++;
        }
        if (scenario.expectedGrounded() != null) {
            checks++;
        }
        return checks;
    }

    private static int evaluateQualitySignals(
            AgentEvaluationScenario scenario,
            AgentEvaluationExecutionMetadata metadata,
            List<String> reasons) {
        int passedChecks = 0;
        if (scenario.expectedIntent() != null) {
            if (metadata.routedIntent() == null) {
                reasons.add("INTENT_METRIC_UNAVAILABLE");
            } else if (scenario.expectedIntent().equalsIgnoreCase(metadata.routedIntent())) {
                passedChecks++;
            } else {
                reasons.add("INTENT_MISMATCH");
            }
        }
        if (scenario.expectedEntityTypes() != null) {
            if (metadata.resolvedEntityTypes() == null) {
                reasons.add("ENTITY_EXTRACTION_METRIC_UNAVAILABLE");
            } else if (new HashSet<>(scenario.expectedEntityTypes())
                    .equals(new HashSet<>(metadata.resolvedEntityTypes()))) {
                passedChecks++;
            } else {
                reasons.add("ENTITY_EXTRACTION_MISMATCH");
            }
        }
        if (scenario.expectedToolName() != null) {
            if (metadata.toolName() == null || metadata.toolSucceeded() == null) {
                reasons.add("TOOL_SUCCESS_METRIC_UNAVAILABLE");
            } else if (scenario.expectedToolName().equals(metadata.toolName())
                    && metadata.toolSucceeded()) {
                passedChecks++;
            } else {
                reasons.add("TOOL_SUCCESS_MISMATCH");
            }
        }
        if (scenario.expectedGrounded() != null) {
            if (metadata.grounded() == null) {
                reasons.add("RAG_GROUNDING_METRIC_UNAVAILABLE");
            } else if (scenario.expectedGrounded().equals(metadata.grounded())) {
                passedChecks++;
            } else {
                reasons.add("RAG_GROUNDING_MISMATCH");
            }
        }
        return passedChecks;
    }

    private static void addUnavailableQualitySignals(
            AgentEvaluationScenario scenario,
            List<String> reasons) {
        if (scenario.expectedIntent() != null) {
            reasons.add("INTENT_METRIC_UNAVAILABLE");
        }
        if (scenario.expectedEntityTypes() != null) {
            reasons.add("ENTITY_EXTRACTION_METRIC_UNAVAILABLE");
        }
        if (scenario.expectedToolName() != null) {
            reasons.add("TOOL_SUCCESS_METRIC_UNAVAILABLE");
        }
        if (scenario.expectedGrounded() != null) {
            reasons.add("RAG_GROUNDING_METRIC_UNAVAILABLE");
        }
    }
}
