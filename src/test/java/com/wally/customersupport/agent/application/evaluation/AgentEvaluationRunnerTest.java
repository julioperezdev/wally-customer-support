package com.wally.customersupport.agent.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.math.BigDecimal;

import com.wally.customersupport.agent.domain.model.AgentEvaluationExecution;
import com.wally.customersupport.agent.domain.model.AgentEvaluationExecutionMetadata;
import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import com.wally.customersupport.agent.domain.model.AgentEvaluationSuiteResult;
import com.wally.customersupport.conversation.application.service.DeterministicResponseHumanizer;
import org.junit.jupiter.api.Test;

class AgentEvaluationRunnerTest {

    private final AgentEvaluationRunner runner = new AgentEvaluationRunner(new ResponsePolicyEvaluator());
    private final DeterministicResponseHumanizer humanizer = new DeterministicResponseHumanizer();

    @Test
    void runsTheVersionedDatasetInDeterministicOrderAndAggregatesMetrics() {
        List<AgentEvaluationScenario> scenarios = new ArrayList<>(CatalogResponseEvaluationDataset.scenarios());
        Collections.reverse(scenarios);

        AgentEvaluationSuiteResult result = runner.run(scenarios, this::executeWithMetadata);

        assertThat(result.datasetVersion()).isEqualTo(CatalogResponseEvaluationDataset.VERSION);
        assertThat(result.scenarioResults()).extracting("scenarioId")
                .containsExactly(
                        "catalog-alternatives",
                        "catalog-clarification",
                        "catalog-matched",
                        "catalog-no-match",
                        "catalog-safe-fallback");
        assertThat(result.totalScenarios()).isEqualTo(5);
        assertThat(result.passedScenarios()).isEqualTo(5);
        assertThat(result.failedScenarios()).isZero();
        assertThat(result.passRate()).isEqualTo(1.0);
        assertThat(result.averageScore()).isEqualTo(1.0);
        assertThat(result.failureReasons()).isEmpty();
        assertThat(result.scenarioResults().getFirst().executionMetadata().modelId())
                .isEqualTo("deterministic-v1");
    }

    @Test
    void aggregatesSanitizedFailureReasonsWithoutRetainingResponseText() {
        AgentEvaluationSuiteResult result = runner.run(
                CatalogResponseEvaluationDataset.scenarios(),
                scenario -> scenario.scenarioId().equals("catalog-matched")
                        ? null
                        : executeWithMetadata(scenario));

        assertThat(result.totalScenarios()).isEqualTo(5);
        assertThat(result.passedScenarios()).isEqualTo(4);
        assertThat(result.failedScenarios()).isEqualTo(1);
        assertThat(result.passRate()).isEqualTo(0.8);
        assertThat(result.averageScore()).isEqualTo(0.8);
        assertThat(result.failureReasons()).containsEntry("RESPONSE_MISSING", 1);
        assertThat(result.toString()).doesNotContain("Remera NullPointer");
    }

    @Test
    void preservesUnavailableUsageAsUnavailableMetadata() {
        AgentEvaluationScenario scenario = CatalogResponseEvaluationDataset.scenarios().getFirst();
        AgentEvaluationSuiteResult result = runner.run(
                List.of(scenario),
                ignored -> new AgentEvaluationExecution(
                        humanizer.humanize(scenario.request()),
                        new AgentEvaluationExecutionMetadata(
                                "catalog-specialist",
                                "v1",
                                "mock",
                                "deterministic-v1",
                                12,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)));

        assertThat(result.scenarioResults().getFirst().executionMetadata().inputTokens()).isNull();
        assertThat(result.scenarioResults().getFirst().executionMetadata().estimatedCostUsd()).isNull();
    }

    @Test
    void rejectsNegativeOperationalMetadata() {
        assertThatThrownBy(() -> new AgentEvaluationExecutionMetadata(
                "catalog-specialist",
                "v1",
                "mock",
                "deterministic-v1",
                -1,
                null,
                null,
                null,
                null,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("durationMs must not be negative");

        assertThatThrownBy(() -> new AgentEvaluationExecutionMetadata(
                "catalog-specialist",
                "v1",
                "mock",
                "deterministic-v1",
                1,
                null,
                -1,
                null,
                null,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("inputTokens must not be negative");
    }

    @Test
    void rejectsEmptyAndMixedVersionSuites() {
        assertThatThrownBy(() -> runner.run(List.of(), scenario -> null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scenarios must not be empty");

        AgentEvaluationScenario first = CatalogResponseEvaluationDataset.scenarios().getFirst();
        AgentEvaluationScenario duplicateId = new AgentEvaluationScenario(
                first.scenarioId(),
                "catalog-response-v2",
                first.useCase(),
                first.channel(),
                first.request(),
                first.expectedOutcome(),
                first.requiredTextFragments(),
                first.forbiddenTextFragments());

        assertThatThrownBy(() -> runner.run(List.of(first, duplicateId), scenario -> null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("scenario ids must be unique");

        AgentEvaluationScenario differentIdAndVersion = new AgentEvaluationScenario(
                "catalog-v2-scenario",
                "catalog-response-v2",
                first.useCase(),
                first.channel(),
                first.request(),
                first.expectedOutcome(),
                first.requiredTextFragments(),
                first.forbiddenTextFragments());
        assertThatThrownBy(() -> runner.run(List.of(first, differentIdAndVersion), scenario -> null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("all scenarios must use the same datasetVersion");
    }

    private AgentEvaluationExecution executeWithMetadata(AgentEvaluationScenario scenario) {
        return new AgentEvaluationExecution(
                humanizer.humanize(scenario.request()),
                new AgentEvaluationExecutionMetadata(
                        "catalog-specialist",
                        "v1",
                        "mock",
                        "deterministic-v1",
                        12,
                        8L,
                        100,
                        40,
                        140,
                        new BigDecimal("0.0001"),
                        "test-pricing-v1"));
    }
}
