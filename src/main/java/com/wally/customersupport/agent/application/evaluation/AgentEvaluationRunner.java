package com.wally.customersupport.agent.application.evaluation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.wally.customersupport.agent.domain.model.AgentEvaluationResult;
import com.wally.customersupport.agent.domain.model.AgentEvaluationExecution;
import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import com.wally.customersupport.agent.domain.model.AgentEvaluationSuiteResult;
import org.springframework.stereotype.Component;

/** Executes a versioned evaluation suite and returns only sanitized metrics. */
@Component
public class AgentEvaluationRunner {

    private final ResponsePolicyEvaluator evaluator;

    public AgentEvaluationRunner(ResponsePolicyEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    public AgentEvaluationSuiteResult run(
            List<AgentEvaluationScenario> scenarios,
            AgentEvaluationExecutor executor) {
        List<AgentEvaluationScenario> orderedScenarios = validateAndOrder(scenarios, executor);
        String datasetVersion = orderedScenarios.getFirst().datasetVersion();
        List<AgentEvaluationResult> results = orderedScenarios.stream()
                .map(scenario -> evaluate(scenario, executor.execute(scenario)))
                .toList();

        int passedScenarios = (int) results.stream().filter(AgentEvaluationResult::passed).count();
        Map<String, Integer> failureReasons = results.stream()
                .flatMap(result -> result.reasons().stream())
                .sorted()
                .collect(Collectors.toMap(
                        Function.identity(),
                        ignored -> 1,
                        Integer::sum,
                        LinkedHashMap::new));
        double averageScore = results.stream()
                .mapToDouble(AgentEvaluationResult::score)
                .average()
                .orElseThrow();

        return new AgentEvaluationSuiteResult(
                datasetVersion,
                results,
                results.size(),
                passedScenarios,
                results.size() - passedScenarios,
                (double) passedScenarios / results.size(),
                averageScore,
                failureReasons);
    }

    private AgentEvaluationResult evaluate(
            AgentEvaluationScenario scenario,
            AgentEvaluationExecution execution) {
        if (execution == null) {
            return evaluator.evaluate(scenario, null);
        }
        return evaluator.evaluate(scenario, execution.response(), execution.metadata());
    }

    private static List<AgentEvaluationScenario> validateAndOrder(
            List<AgentEvaluationScenario> scenarios,
            AgentEvaluationExecutor executor) {
        if (scenarios == null || scenarios.isEmpty()) {
            throw new IllegalArgumentException("scenarios must not be empty");
        }
        Objects.requireNonNull(executor, "executor");

        List<AgentEvaluationScenario> ordered = new ArrayList<>(scenarios);
        ordered.forEach(scenario -> Objects.requireNonNull(scenario, "scenarios must not contain null"));
        ordered.sort(Comparator.comparing(AgentEvaluationScenario::scenarioId));

        long distinctIds = ordered.stream()
                .map(AgentEvaluationScenario::scenarioId)
                .distinct()
                .count();
        if (distinctIds != ordered.size()) {
            throw new IllegalArgumentException("scenario ids must be unique");
        }

        String datasetVersion = ordered.getFirst().datasetVersion();
        if (ordered.stream().anyMatch(scenario -> !datasetVersion.equals(scenario.datasetVersion()))) {
            throw new IllegalArgumentException("all scenarios must use the same datasetVersion");
        }
        return List.copyOf(ordered);
    }
}
