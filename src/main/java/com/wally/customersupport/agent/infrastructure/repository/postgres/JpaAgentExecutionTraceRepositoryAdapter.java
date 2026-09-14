package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.util.List;

import com.wally.customersupport.agent.application.port.out.AgentExecutionTraceRepository;
import com.wally.customersupport.agent.domain.model.AgentExecutionTrace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaAgentExecutionTraceRepositoryAdapter implements AgentExecutionTraceRepository {

    private final SpringDataAgentExecutionTraceRepository repository;

    @Override
    @Transactional
    public AgentExecutionTrace save(AgentExecutionTrace trace) {
        return repository.saveAndFlush(new AgentExecutionTraceJpaEntity(trace)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentExecutionTrace> findRecent(String agentId, String useCase, int limit) {
        String agentFilter = agentId == null || agentId.isBlank() ? null : agentId.strip();
        String useCaseFilter = useCase == null || useCase.isBlank() ? null : useCase.strip();
        return repository.findTop500ByOrderByExecutedAtDesc().stream()
                .map(AgentExecutionTraceJpaEntity::toDomain)
                .filter(trace -> agentFilter == null || agentFilter.equals(trace.agentId()))
                .filter(trace -> useCaseFilter == null || useCaseFilter.equals(trace.useCase()))
                .limit(Math.max(1, Math.min(limit, 500)))
                .toList();
    }
}
