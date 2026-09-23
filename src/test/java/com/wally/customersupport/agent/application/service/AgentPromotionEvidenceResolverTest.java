package com.wally.customersupport.agent.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationComparison;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationComparisonAssessment;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationMetricDelta;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationQualityMetricDelta;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRunSummary;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationScenarioComparison;
import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.agent.domain.model.AgentInvocationConfiguration;
import com.wally.customersupport.agent.domain.model.AgentLifecyclePolicy;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import com.wally.customersupport.agent.domain.model.AgentVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentPromotionEvidenceResolverTest {

    private static final UUID BASELINE_RUN = UUID.fromString("00000000-0000-0000-0000-000000000031");
    private static final UUID CANDIDATE_RUN = UUID.fromString("00000000-0000-0000-0000-000000000032");
    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    private AgentEvaluationComparisonApplicationService comparisonService;
    private AgentRegistryRepository registry;
    private AgentPromotionEvidenceResolver resolver;
    private AgentEvaluationComparison comparison;
    private AgentVersion activeBaseline;
    private AgentVersion candidate;

    @BeforeEach
    void setUp() {
        comparisonService = mock(AgentEvaluationComparisonApplicationService.class);
        registry = mock(AgentRegistryRepository.class);
        resolver = new AgentPromotionEvidenceResolver(comparisonService, registry);
        comparison = comparison();
        activeBaseline = version(1, AgentLifecycleState.ACTIVE);
        candidate = version(2, AgentLifecycleState.EVALUATED);
        when(comparisonService.compare(BASELINE_RUN, CANDIDATE_RUN)).thenReturn(Optional.of(comparison));
        when(registry.findVersion("support-specialist", 1)).thenReturn(Optional.of(activeBaseline));
    }

    @Test
    void resolvesComparableEvidenceOnlyForTheCandidateAndItsActiveBaseline() {
        var evidence = resolver.resolve(candidate, BASELINE_RUN, CANDIDATE_RUN);

        assertThat(evidence.baselineRunId()).isEqualTo(BASELINE_RUN);
        assertThat(evidence.candidateRunId()).isEqualTo(CANDIDATE_RUN);
        assertThat(evidence.datasetVersion()).isEqualTo("support-eval-v1");
        assertThat(evidence.assessmentOutcome()).isEqualTo("QUALITY_IMPROVED");
    }

    @Test
    void rejectsEvidenceForAnotherCandidateVersion() {
        when(comparisonService.compare(BASELINE_RUN, CANDIDATE_RUN)).thenReturn(Optional.of(comparison(
                "support-specialist", "1", "support-specialist", "1")));

        assertThatThrownBy(() -> resolver.resolve(candidate, BASELINE_RUN, CANDIDATE_RUN))
                .isInstanceOf(AgentPromotionEvidenceException.class)
                .extracting(exception -> ((AgentPromotionEvidenceException) exception).reason())
                .isEqualTo(AgentPromotionEvidenceException.Reason.CANDIDATE_VERSION_MISMATCH);
    }

    @Test
    void rejectsEvidenceWhoseBaselineIsNoLongerActive() {
        when(registry.findVersion("support-specialist", 1))
                .thenReturn(Optional.of(version(1, AgentLifecycleState.APPROVED)));

        assertThatThrownBy(() -> resolver.resolve(candidate, BASELINE_RUN, CANDIDATE_RUN))
                .isInstanceOf(AgentPromotionEvidenceException.class)
                .extracting(exception -> ((AgentPromotionEvidenceException) exception).reason())
                .isEqualTo(AgentPromotionEvidenceException.Reason.BASELINE_NOT_ACTIVE);
    }

    private static AgentEvaluationComparison comparison() {
        return comparison("support-specialist", "1", "support-specialist", "2");
    }

    private static AgentEvaluationComparison comparison(
            String baselineAgent,
            String baselineVersion,
            String candidateAgent,
            String candidateVersion) {
        AgentEvaluationRunSummary baseline = summary(BASELINE_RUN, baselineAgent, baselineVersion);
        AgentEvaluationRunSummary candidate = summary(CANDIDATE_RUN, candidateAgent, candidateVersion);
        return new AgentEvaluationComparison(
                BASELINE_RUN,
                CANDIDATE_RUN,
                "support-eval-v1",
                baseline,
                candidate,
                new AgentEvaluationMetricDelta(1, -1, 1, 0.2, 20,
                        OptionalLong.empty(), OptionalLong.empty(), Optional.empty()),
                List.of(new AgentEvaluationScenarioComparison("scenario-1", false, true, 0.5, 0.9, 0.4)),
                new AgentEvaluationQualityMetricDelta(1, 0, 0, 0, 0, null, null, null, null),
                new AgentEvaluationComparisonAssessment(
                        AgentEvaluationComparisonAssessment.Outcome.QUALITY_IMPROVED,
                        1, 1, 0, 0, List.of("pass_rate"), List.of(), List.of(),
                        AgentEvaluationComparisonAssessment.EvidenceLevel.DESCRIPTIVE_NOT_STATISTICALLY_SIGNIFICANT));
    }

    private static AgentEvaluationRunSummary summary(UUID runId, String agentId, String agentVersion) {
        return new AgentEvaluationRunSummary(
                runId, "support-eval-v1", agentId, agentVersion, "bedrock", "model-1", NOW, NOW,
                10, 1, 1, 0, 1, 0.9, Map.of(), null, null, null);
    }

    private static AgentVersion version(int number, AgentLifecycleState targetState) {
        AgentVersion draft = AgentVersion.draft(
                "support-specialist", number, "1.0." + number, "Support specialist", "Grounded support",
                "bedrock", "model-1", new AgentInferenceParameters(BigDecimal.ZERO, BigDecimal.ONE),
                "system-v1", "0".repeat(64), "input-v1", "output-v1", Set.of(), Set.of(),
                "memory-v1", "grounded-v1", Duration.ofSeconds(10), 1, 100, 100,
                BigDecimal.ONE, null, "support-eval-v1", AgentInvocationConfiguration.empty(), "tester", NOW);
        List<AgentLifecycleState> progression = List.of(
                AgentLifecycleState.CANDIDATE,
                AgentLifecycleState.EVALUATED,
                AgentLifecycleState.APPROVED,
                AgentLifecycleState.ACTIVE);
        AgentVersion current = draft;
        for (AgentLifecycleState state : progression) {
            current = new AgentLifecyclePolicy().transition(current, state, "tester", NOW);
            if (state == targetState) {
                return current;
            }
        }
        if (targetState == AgentLifecycleState.DRAFT) {
            return draft;
        }
        throw new IllegalArgumentException("unsupported test lifecycle state " + targetState);
    }
}
