package com.wally.customersupport.agent.application.evaluation;

import com.wally.customersupport.agent.domain.model.AgentEvaluationExecution;
import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import com.wally.customersupport.conversation.application.port.out.ResponseHumanizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * Explicit baseline executor for the evaluation control plane.
 *
 * <p>It evaluates the current deterministic response policy only. A future
 * Bedrock executor can replace this component behind the same contract after
 * its model, budget and evaluation policy are approved.</p>
 */
@Component
@ConditionalOnProperty(name = "wcs.agent-evaluation.executor", havingValue = "deterministic", matchIfMissing = true)
@RequiredArgsConstructor
public class DeterministicResponsePolicyEvaluationExecutor implements AgentEvaluationExecutor {

    private final ResponseHumanizer responseHumanizer;

    @Override
    public void validate(AgentEvaluationRunRequest request) {
        if (request == null || !"mock".equalsIgnoreCase(request.provider())) {
            throw new IllegalArgumentException("evaluation provider is not supported by deterministic executor");
        }
    }

    @Override
    public AgentEvaluationExecution execute(AgentEvaluationScenario scenario) {
        return new AgentEvaluationExecution(
                responseHumanizer.humanize(scenario.request()),
                null);
    }
}
