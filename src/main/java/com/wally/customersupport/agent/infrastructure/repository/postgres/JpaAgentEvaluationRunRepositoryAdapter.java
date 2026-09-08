package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryFilter;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPage;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPageRequest;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRun;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaAgentEvaluationRunRepositoryAdapter implements AgentEvaluationRunRepository {

    private final SpringDataAgentEvaluationRunRepository repository;

    @Override
    @Transactional
    public AgentEvaluationRun save(AgentEvaluationRun run) {
        if (repository.existsById(run.runId())) {
            throw new IllegalStateException("evaluation runs are immutable once persisted");
        }
        return repository.saveAndFlush(new AgentEvaluationRunJpaEntity(run)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentEvaluationRun> findById(UUID runId) {
        return repository.findById(runId).map(AgentEvaluationRunJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public AgentEvaluationHistoryPage search(
            AgentEvaluationHistoryFilter filter,
            AgentEvaluationHistoryPageRequest pageRequest) {
        Page<AgentEvaluationRunJpaEntity> page = repository.search(
                filter.datasetVersion(),
                filter.agentId(),
                filter.agentVersion(),
                filter.provider(),
                filter.modelId(),
                filter.completedFrom(),
                filter.completedTo(),
                PageRequest.of(
                        pageRequest.pageNumber(),
                        pageRequest.pageSize(),
                        Sort.by(
                                Sort.Order.desc("completedAt"),
                                Sort.Order.asc("id"))));
        return new AgentEvaluationHistoryPage(
                page.getContent().stream().map(AgentEvaluationRunJpaEntity::toSummary).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
