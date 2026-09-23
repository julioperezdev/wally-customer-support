package com.wally.customersupport.agent.application.evaluation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.wally.customersupport.agent.domain.model.AgentEvaluationExecutionMetadata;
import com.wally.customersupport.agent.domain.model.AgentEvaluationResult;
import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import org.springframework.stereotype.Component;

/** Evaluates sanitized humanization and router quality signals without retaining generated text. */
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
                scenario.scenarioId(), scenario.datasetVersion(), passed, score, reasons,
                executionMetadata, evaluatedDimensions(scenario));
    }

    /** Scores a structured router proposal against synthetic expected intent, action and filters. */
    public AgentEvaluationResult evaluateRoute(
            AgentEvaluationScenario scenario,
            ConversationIntentDecision actual,
            AgentEvaluationExecutionMetadata executionMetadata) {
        if (scenario == null || scenario.routingContext() == null) {
            throw new IllegalArgumentException("scenario must contain a routingContext");
        }
        List<String> reasons = new ArrayList<>();
        int passedChecks = 0;
        int checks = scenario.expectedQuantity() == null ? 3 : 4;
        if (actual == null) {
            reasons.add("ROUTER_DECISION_MISSING");
        } else {
            if (scenario.expectedIntent().equalsIgnoreCase(actual.intent().name())) {
                passedChecks++;
            } else {
                reasons.add("INTENT_MISMATCH");
            }
            if (scenario.expectedAction().equalsIgnoreCase(actual.action().name())) {
                passedChecks++;
            } else {
                reasons.add("ACTION_MISMATCH");
            }
            if (scenario.expectedEntityTypes().stream().sorted().toList()
                    .equals(queryAttributes(actual.catalogQuery()))) {
                passedChecks++;
            } else {
                reasons.add("ENTITY_EXTRACTION_MISMATCH");
            }
            if (scenario.expectedQuantity() != null) {
                if (scenario.expectedQuantity() == actual.quantity()) passedChecks++;
                else reasons.add("QUANTITY_MISMATCH");
            }
        }
        List<String> dimensions = new ArrayList<>(List.of("intent_accuracy", "action_accuracy", "entity_extraction"));
        if (scenario.expectedQuantity() != null) dimensions.add("quantity_extraction");
        return new AgentEvaluationResult(
                scenario.scenarioId(), scenario.datasetVersion(), actual != null && reasons.isEmpty(),
                (double) passedChecks / checks, reasons, executionMetadata, dimensions);
    }

    private static List<String> evaluatedDimensions(AgentEvaluationScenario scenario) {
        List<String> dimensions = new ArrayList<>();
        if (scenario.expectedIntent() != null) dimensions.add("intent_accuracy");
        if (scenario.expectedEntityTypes() != null) dimensions.add("entity_extraction");
        if (scenario.expectedAction() != null) dimensions.add("action_accuracy");
        if (scenario.expectedQuantity() != null) dimensions.add("quantity_extraction");
        if (scenario.expectedToolName() != null) dimensions.add("tool_success");
        if (scenario.expectedGrounded() != null) dimensions.add("rag_grounding");
        return dimensions;
    }

    private static int qualitySignalChecks(AgentEvaluationScenario scenario) {
        int checks = 0;
        if (scenario.expectedIntent() != null) checks++;
        if (scenario.expectedEntityTypes() != null) checks++;
        if (scenario.expectedAction() != null) checks++;
        if (scenario.expectedQuantity() != null) checks++;
        if (scenario.expectedToolName() != null) checks++;
        if (scenario.expectedGrounded() != null) checks++;
        return checks;
    }

    private static int evaluateQualitySignals(
            AgentEvaluationScenario scenario,
            AgentEvaluationExecutionMetadata metadata,
            List<String> reasons) {
        int passedChecks = 0;
        if (scenario.expectedIntent() != null) {
            if (metadata.routedIntent() == null) reasons.add("INTENT_METRIC_UNAVAILABLE");
            else if (scenario.expectedIntent().equalsIgnoreCase(metadata.routedIntent())) passedChecks++;
            else reasons.add("INTENT_MISMATCH");
        }
        if (scenario.expectedEntityTypes() != null) {
            if (metadata.resolvedEntityTypes() == null) reasons.add("ENTITY_EXTRACTION_METRIC_UNAVAILABLE");
            else if (new HashSet<>(scenario.expectedEntityTypes())
                    .equals(new HashSet<>(metadata.resolvedEntityTypes()))) passedChecks++;
            else reasons.add("ENTITY_EXTRACTION_MISMATCH");
        }
        if (scenario.expectedAction() != null) {
            if (metadata.routedAction() == null) reasons.add("ACTION_METRIC_UNAVAILABLE");
            else if (scenario.expectedAction().equalsIgnoreCase(metadata.routedAction())) passedChecks++;
            else reasons.add("ACTION_MISMATCH");
        }
        if (scenario.expectedQuantity() != null) {
            if (metadata.routedQuantity() == null) reasons.add("QUANTITY_METRIC_UNAVAILABLE");
            else if (scenario.expectedQuantity().equals(metadata.routedQuantity())) passedChecks++;
            else reasons.add("QUANTITY_MISMATCH");
        }
        if (scenario.expectedToolName() != null) {
            if (metadata.toolName() == null || metadata.toolSucceeded() == null) reasons.add("TOOL_SUCCESS_METRIC_UNAVAILABLE");
            else if (scenario.expectedToolName().equals(metadata.toolName()) && metadata.toolSucceeded()) passedChecks++;
            else reasons.add("TOOL_SUCCESS_MISMATCH");
        }
        if (scenario.expectedGrounded() != null) {
            if (metadata.grounded() == null) reasons.add("RAG_GROUNDING_METRIC_UNAVAILABLE");
            else if (scenario.expectedGrounded().equals(metadata.grounded())) passedChecks++;
            else reasons.add("RAG_GROUNDING_MISMATCH");
        }
        return passedChecks;
    }

    private static void addUnavailableQualitySignals(
            AgentEvaluationScenario scenario,
            List<String> reasons) {
        if (scenario.expectedIntent() != null) reasons.add("INTENT_METRIC_UNAVAILABLE");
        if (scenario.expectedEntityTypes() != null) reasons.add("ENTITY_EXTRACTION_METRIC_UNAVAILABLE");
        if (scenario.expectedAction() != null) reasons.add("ACTION_METRIC_UNAVAILABLE");
        if (scenario.expectedQuantity() != null) reasons.add("QUANTITY_METRIC_UNAVAILABLE");
        if (scenario.expectedToolName() != null) reasons.add("TOOL_SUCCESS_METRIC_UNAVAILABLE");
        if (scenario.expectedGrounded() != null) reasons.add("RAG_GROUNDING_METRIC_UNAVAILABLE");
    }

    private static List<String> queryAttributes(CatalogQuery query) {
        if (query == null) return List.of();
        List<String> attributes = new ArrayList<>();
        addAttribute(attributes, "name", query.name());
        addAttribute(attributes, "sku", query.sku());
        addAttribute(attributes, "productType", query.productType());
        addAttribute(attributes, "color", query.color());
        addAttribute(attributes, "size", query.size());
        addAttribute(attributes, "minPrice", query.minPrice() == null
                ? null : query.minPrice().stripTrailingZeros().toPlainString());
        addAttribute(attributes, "maxPrice", query.maxPrice() == null
                ? null : query.maxPrice().stripTrailingZeros().toPlainString());
        return attributes.stream().sorted().toList();
    }

    private static void addAttribute(List<String> target, String name, String value) {
        if (value != null && !value.isBlank()) target.add(name + "=" + value);
    }
}
