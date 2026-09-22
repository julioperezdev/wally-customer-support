package com.wally.customersupport.agent.application.service;

import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationComparison;
import com.wally.customersupport.agent.application.registry.AgentPromotionEvaluationEvidence;
import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import com.wally.customersupport.agent.domain.model.AgentVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Resolves promotion evidence and verifies it against the immutable candidate and active baseline. */
@Service
@RequiredArgsConstructor
public class AgentPromotionEvidenceResolver {

    private final AgentEvaluationComparisonApplicationService comparisonService;
    private final AgentRegistryRepository registryRepository;

    public AgentPromotionEvaluationEvidence resolve(
            AgentVersion candidateVersion,
            UUID baselineRunId,
            UUID candidateRunId) {
        if (baselineRunId == null || candidateRunId == null) {
            throw new AgentPromotionEvidenceException(AgentPromotionEvidenceException.Reason.REQUIRED);
        }

        AgentEvaluationComparison comparison;
        try {
            comparison = comparisonService.compare(baselineRunId, candidateRunId)
                    .orElseThrow(() -> new AgentPromotionEvidenceException(
                            AgentPromotionEvidenceException.Reason.RUN_NOT_FOUND));
        } catch (IncompatibleEvaluationRunsException exception) {
            throw new AgentPromotionEvidenceException(AgentPromotionEvidenceException.Reason.RUNS_NOT_COMPARABLE);
        }

        if (!candidateVersion.agentId().equals(comparison.candidate().agentId())
                || !Integer.toString(candidateVersion.version()).equals(comparison.candidate().agentVersion())) {
            throw new AgentPromotionEvidenceException(
                    AgentPromotionEvidenceException.Reason.CANDIDATE_VERSION_MISMATCH);
        }
        if (!candidateVersion.evaluationSuiteVersion().equals(comparison.datasetVersion())) {
            throw new AgentPromotionEvidenceException(AgentPromotionEvidenceException.Reason.DATASET_MISMATCH);
        }

        int baselineVersion;
        try {
            baselineVersion = Integer.parseInt(comparison.baseline().agentVersion());
        } catch (NumberFormatException exception) {
            throw new AgentPromotionEvidenceException(AgentPromotionEvidenceException.Reason.BASELINE_NOT_ACTIVE);
        }
        AgentVersion baselineDefinition = registryRepository
                .findVersion(candidateVersion.agentId(), baselineVersion)
                .orElseThrow(() -> new AgentPromotionEvidenceException(
                        AgentPromotionEvidenceException.Reason.BASELINE_NOT_ACTIVE));
        if (baselineDefinition.state() != AgentLifecycleState.ACTIVE
                || !baselineDefinition.evaluationSuiteVersion().equals(comparison.datasetVersion())) {
            throw new AgentPromotionEvidenceException(AgentPromotionEvidenceException.Reason.BASELINE_NOT_ACTIVE);
        }

        return new AgentPromotionEvaluationEvidence(
                comparison.baselineRunId(),
                comparison.candidateRunId(),
                comparison.datasetVersion(),
                comparison.assessment().outcome().name());
    }
}
