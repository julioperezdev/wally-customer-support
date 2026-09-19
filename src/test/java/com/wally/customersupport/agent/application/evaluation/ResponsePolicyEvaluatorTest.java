package com.wally.customersupport.agent.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.math.BigDecimal;

import com.wally.customersupport.agent.domain.model.AgentEvaluationExecutionMetadata;
import com.wally.customersupport.agent.domain.model.AgentEvaluationResult;
import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import com.wally.customersupport.conversation.application.service.DeterministicResponseHumanizer;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import org.junit.jupiter.api.Test;

class ResponsePolicyEvaluatorTest {

    private final ResponsePolicyEvaluator evaluator = new ResponsePolicyEvaluator();
    private final DeterministicResponseHumanizer humanizer = new DeterministicResponseHumanizer();

    @Test
    void evaluatesTheSyntheticCatalogDatasetWithoutARealModel() {
        List<AgentEvaluationResult> results = CatalogResponseEvaluationDataset.scenarios().stream()
                .map(scenario -> evaluator.evaluate(
                        scenario,
                        humanizer.humanize(scenario.request())))
                .toList();

        assertThat(results).hasSize(5)
                .allMatch(AgentEvaluationResult::passed)
                .allMatch(result -> result.score() == 1.0);
    }

    @Test
    void detectsMissingAndForbiddenTextWithoutLoggingTheResponse() {
        AgentEvaluationScenario scenario = CatalogResponseEvaluationDataset.scenarios().getFirst();
        ResponseHumanizationResult invalid = ResponseHumanizationResult.applied(
                "Encontré estos productos, precio especial incluido.",
                DeterministicResponseHumanizer.POLICY_ID,
                DeterministicResponseHumanizer.POLICY_VERSION);

        AgentEvaluationResult result = evaluator.evaluate(scenario, invalid);

        assertThat(result.passed()).isFalse();
        assertThat(result.score()).isLessThan(1.0);
        assertThat(result.reasons()).contains("REQUIRED_TEXT_MISSING", "FORBIDDEN_TEXT_PRESENT");
        assertThat(result.reasons()).doesNotContain("Encontré estos productos, precio especial incluido.");
    }

    @Test
    void detectsAnUnexpectedOutcome() {
        AgentEvaluationScenario scenario = CatalogResponseEvaluationDataset.scenarios().get(1);
        ResponseHumanizationResult invalid = ResponseHumanizationResult.fallback(
                "No pude preparar una respuesta segura para esa consulta.",
                DeterministicResponseHumanizer.POLICY_ID,
                DeterministicResponseHumanizer.POLICY_VERSION,
                "TEST");

        AgentEvaluationResult result = evaluator.evaluate(scenario, invalid);

        assertThat(result.passed()).isFalse();
        assertThat(result.reasons()).contains("OUTCOME_MISMATCH");
    }

    @Test
    void evaluatesRoutingEntitiesToolAndGroundingSignalsWhenTheScenarioDeclaresThem() {
        AgentEvaluationScenario base = CatalogResponseEvaluationDataset.scenarios().getFirst();
        AgentEvaluationScenario scenario = new AgentEvaluationScenario(
                base.scenarioId(), base.datasetVersion(), base.useCase(), base.channel(), base.request(),
                base.expectedOutcome(), base.requiredTextFragments(), base.forbiddenTextFragments(),
                "CATALOG_SEARCH", List.of("productType", "color", "size"), "catalog.search", true);
        ResponseHumanizationResult response = humanizer.humanize(scenario.request());
        AgentEvaluationExecutionMetadata metadata = new AgentEvaluationExecutionMetadata(
                "catalog-specialist", "v1", "bedrock", "model-a", 120, 100L, 20, 10, 30,
                new BigDecimal("0.001"), "pricing-v1", "CATALOG_SEARCH",
                List.of("size", "color", "productType"), "catalog.search", true, true);

        AgentEvaluationResult result = evaluator.evaluate(scenario, response, metadata);

        assertThat(result.passed()).isTrue();
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void reportsStableReasonsWhenQualitySignalsDisagree() {
        AgentEvaluationScenario base = CatalogResponseEvaluationDataset.scenarios().getFirst();
        AgentEvaluationScenario scenario = new AgentEvaluationScenario(
                base.scenarioId(), base.datasetVersion(), base.useCase(), base.channel(), base.request(),
                base.expectedOutcome(), base.requiredTextFragments(), base.forbiddenTextFragments(),
                "CATALOG_SEARCH", List.of("productType"), "catalog.search", true);
        AgentEvaluationExecutionMetadata metadata = new AgentEvaluationExecutionMetadata(
                "catalog-specialist", "v1", "bedrock", "model-a", 120, 100L, 20, 10, 30,
                new BigDecimal("0.001"), "pricing-v1", "GENERAL_SUPPORT",
                List.of("color"), "knowledge.retrieve", false, false);

        AgentEvaluationResult result = evaluator.evaluate(
                scenario, humanizer.humanize(scenario.request()), metadata);

        assertThat(result.passed()).isFalse();
        assertThat(result.reasons()).contains(
                "INTENT_MISMATCH",
                "ENTITY_EXTRACTION_MISMATCH",
                "TOOL_SUCCESS_MISMATCH",
                "RAG_GROUNDING_MISMATCH");
    }
}
