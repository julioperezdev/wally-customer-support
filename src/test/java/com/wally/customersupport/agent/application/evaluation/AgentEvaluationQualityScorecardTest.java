package com.wally.customersupport.agent.application.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.math.BigDecimal;

import com.wally.customersupport.agent.domain.model.AgentEvaluationExecutionMetadata;
import com.wally.customersupport.agent.domain.model.AgentEvaluationQualityScorecard;
import com.wally.customersupport.agent.domain.model.AgentEvaluationResult;
import com.wally.customersupport.agent.domain.model.AgentEvaluationScenario;
import com.wally.customersupport.agent.domain.model.AgentEvaluationSuiteResult;
import com.wally.customersupport.conversation.application.service.DeterministicResponseHumanizer;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ConversationAction;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.conversation.domain.model.ConversationIntent;
import com.wally.customersupport.conversation.domain.model.ConversationIntentDecision;
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
                "action_accuracy", "entity_extraction", "intent_accuracy", "rag_grounding", "tool_success");
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
        assertThat(scorecard.unavailableDimensions()).containsExactly("action_accuracy");
    }

    @Test
    void reportsRouterMetricsWithoutClaimingResponseToolOrRagMetrics() {
        var scenario = new AgentEvaluationScenario(
                "router-case", "conversation-routing-v1", "ROUTING", Channel.TELEGRAM,
                null, ResponseHumanizationResult.Outcome.APPLIED, List.of(), List.of(),
                "CATALOG_SEARCH", List.of("productType=remera"), null, null,
                new ConversationContext(null, null, "Quiero una remera", List.of(), List.of(),
                        null, List.of(), Channel.TELEGRAM), "CATALOG_SEARCH");
        var metadata = new AgentEvaluationExecutionMetadata(
                "conversation-router", "2", "bedrock", "model-v2", 800, 750L,
                250, 70, 320, new BigDecimal("0.00004"), "pricing-v1",
                "CATALOG_SEARCH", "CATALOG_SEARCH", List.of("productType=remera"), null, null, null);
        var decision = new ConversationIntentDecision(
                ConversationIntent.CATALOG_SEARCH, ConversationAction.CATALOG_SEARCH, 0.9,
                new com.wally.customersupport.catalog.domain.model.CatalogQuery(
                        null, null, null, null, "remera"), null, 1, List.of());
        AgentEvaluationResult result = evaluator.evaluateRoute(scenario, decision, metadata);

        AgentEvaluationQualityScorecard scorecard = AgentEvaluationQualityScorecard.fromResults(
                List.of(result), result.score());

        assertThat(scorecard.intentAccuracyRate()).isEqualTo(1.0);
        assertThat(scorecard.entityExtractionRate()).isEqualTo(1.0);
        assertThat(scorecard.actionAccuracyRate()).isEqualTo(1.0);
        assertThat(scorecard.responseValidityRate()).isNull();
        assertThat(scorecard.responseGroundingRate()).isNull();
        assertThat(scorecard.safetyRate()).isNull();
        assertThat(scorecard.utilityRate()).isNull();
        assertThat(scorecard.unavailableDimensions()).containsExactly(
                "rag_grounding", "response_grounding", "response_validity", "safety", "tool_success", "utility");
    }
}
