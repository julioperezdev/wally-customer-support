package com.wally.customersupport.agent.application.evaluation;

import java.util.ArrayList;
import java.util.List;

import com.wally.customersupport.agent.domain.model.AgentEvaluationResult;
import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
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
        if (scenario == null) {
            throw new IllegalArgumentException("scenario must not be null");
        }

        List<String> reasons = new ArrayList<>();
        int checks = 1 + scenario.requiredTextFragments().size()
                + scenario.forbiddenTextFragments().size();
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
        }

        boolean passed = actual != null && reasons.isEmpty();
        double score = (double) passedChecks / checks;
        return new AgentEvaluationResult(
                scenario.scenarioId(),
                scenario.datasetVersion(),
                passed,
                score,
                reasons);
    }
}
