package com.wally.customersupport.agent.application.service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationEvidenceExport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Builds a bounded, versioned export from the sanitized comparison boundary. */
@Service
@RequiredArgsConstructor
public class AgentEvaluationEvidenceExportApplicationService {

    private final AgentEvaluationComparisonApplicationService comparisonService;

    public Optional<AgentEvaluationEvidenceExport> export(UUID baselineRunId, UUID candidateRunId) {
        Objects.requireNonNull(baselineRunId, "baselineRunId");
        Objects.requireNonNull(candidateRunId, "candidateRunId");
        return comparisonService.compare(baselineRunId, candidateRunId)
                .map(AgentEvaluationEvidenceExport::from);
    }
}
