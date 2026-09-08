package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRun;
import com.wally.customersupport.agent.application.port.out.AgentEvaluationRunRepository;
import lombok.RequiredArgsConstructor;
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
}
