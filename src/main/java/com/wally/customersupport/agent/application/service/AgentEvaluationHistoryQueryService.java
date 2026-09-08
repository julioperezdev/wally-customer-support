package com.wally.customersupport.agent.application.service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryFilter;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPage;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPageRequest;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRun;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Internal read-only application boundary for the sanitized evaluation history. */
@Service
@RequiredArgsConstructor
public class AgentEvaluationHistoryQueryService {

    private final AgentEvaluationRunRepository repository;

    public AgentEvaluationHistoryPage search(
            AgentEvaluationHistoryFilter filter,
            AgentEvaluationHistoryPageRequest pageRequest) {
        return Objects.requireNonNull(repository.search(
                Objects.requireNonNull(filter, "filter"),
                Objects.requireNonNull(pageRequest, "pageRequest")),
                "repository.search must not return null");
    }

    public Optional<AgentEvaluationRun> findById(UUID runId) {
        Objects.requireNonNull(runId, "runId");
        return Objects.requireNonNull(repository.findById(runId), "repository.findById must not return null");
    }
}
