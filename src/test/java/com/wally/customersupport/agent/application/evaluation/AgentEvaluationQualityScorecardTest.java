package com.wally.customersupport.agent.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.math.BigDecimal;

import com.wally.customersupport.agent.domain.model.AgentEvaluationExecutionMetadata;
import com.wally.customersupport.agent.domain.model.AgentEvaluationQualityScorecard;
import com.wally.customersupport.agent.domain.model.AgentEvaluationResult;
import com.wally.customersupport.agent.domain.model.AgentEvaluationSuiteResult;
import com.wally.customersupport.conversation.application.service.DeterministicResponseHumanizer;
import com.wally.customersupport.conversation.domain.model.ResponseHumanizationResult;
import org.junit.jupiter.api.Test;

class AgentEvaluationQualityScorecardTest {

    private final ResponsePolicyEvaluator evaluator = new ResponsePolicyEvaluator();
    private final DeterministicResponseHumanizer humanizer = new DeterministicResponseHumanizer();

    @Test
    void reportsMeasuredDimensionsAndExplicitlyMarksUnavailableDimensions() {
        List<AgentEvaluationResult> results = CatalogResponseEvaluationDataset.scenarios().stream()
                .map(scenario -> evaluator.evaluate(scenario, humanizer.humanize(scenario.request())))
                .toList();
        AgentEvaluationSuiteResult suite = new AgentEvaluationSuiteResult(
                CatalogResponseEvaluationDataset.VERSION,
                results,
                results.size(),
                results.size(),
                0,
                1.0,
                1.0,
                java.util.Map.of());

        AgentEvaluationQualityScorecard scorecard = suite.qualityScorecard();

        assertThat(scorecard.responseValidityRate()).isEqualTo(1.0);
        assertThat(scorecard.responseGroundingRate()).isEqualTo(1.0);
        assertThat(scorecard.safetyRate()).isEqualTo(1.0);
        assertThat(scorecard.utilityRate()).isEqualTo(1.0);
        assertThat(scorecard.failureCounts()).isEmpty();
        assertThat(scorecard.unavailableDimensions()).containsExactly(
                "entity_extraction", "intent_accuracy", "rag_grounding", "tool_success");
    }

    @Test
    void doesNotTurnMissingFactsIntoAQualitySuccess() {
        var scenario = CatalogResponseEvaluationDataset.scenarios().getFirst();
        var invalid = ResponseHumanizationResult.applied(
                "Encontré estos productos.",
                DeterministicResponseHumanizer.POLICY_ID,
                DeterministicResponseHumanizer.POLICY_VERSION);
        AgentEvaluationResult result = evaluator.evaluate(scenario, invalid);

        AgentEvaluationQualityScorecard scorecard = AgentEvaluationQualityScorecard.fromResults(
                List.of(result), result.score());

        assertThat(scorecard.responseValidityRate()).isEqualTo(1.0);
        assertThat(scorecard.responseGroundingRate()).isEqualTo(0.0);
        assertThat(scorecard.safetyRate()).isEqualTo(1.0);
        assertThat(scorecard.utilityRate()).isLessThan(1.0);
        assertThat(scorecard.failureCounts()).containsEntry("REQUIRED_TEXT_MISSING", 1);
    }

    @Test
    void exposesQualityRatesWhenExecutionSignalsAreAvailable() {
        AgentEvaluationExecutionMetadata metadata = new AgentEvaluationExecutionMetadata(
                "catalog-specialist", "v1", "bedrock", "model-a", 100, 80L, 10, 4, 14,
                new BigDecimal("0.0012"), "pricing-v1", "CATALOG_SEARCH",
                List.of("productType", "color"), "catalog.search", true, true);
        AgentEvaluationResult result = new AgentEvaluationResult(
                "scenario-with-signals", "dataset-v2", true, 1.0, List.of(), metadata,
                List.of("intent_accuracy", "entity_extraction", "tool_success", "rag_grounding"));

        AgentEvaluationQualityScorecard scorecard = AgentEvaluationQualityScorecard.fromResults(
                List.of(result), 1.0);

        assertThat(scorecard.intentAccuracyRate()).isEqualTo(1.0);
        assertThat(scorecard.entityExtractionRate()).isEqualTo(1.0);
        assertThat(scorecard.toolSuccessRate()).isEqualTo(1.0);
        assertThat(scorecard.ragGroundingRate()).isEqualTo(1.0);
        assertThat(scorecard.unavailableDimensions()).isEmpty();
    }
}
