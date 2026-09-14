package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.util.List;

import com.wally.customersupport.agent.application.port.out.AgentRegistryAuditRepository;
import com.wally.customersupport.agent.domain.model.AgentRegistryAuditEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaAgentRegistryAuditRepositoryAdapter implements AgentRegistryAuditRepository {

    private final SpringDataAgentRegistryAuditRepository repository;

    @Override
    @Transactional
    public AgentRegistryAuditEvent save(AgentRegistryAuditEvent event) {
        return repository.saveAndFlush(new AgentRegistryAuditJpaEntity(event)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentRegistryAuditEvent> findRecent(String agentId, int limit) {
        String filter = agentId == null || agentId.isBlank() ? null : agentId.strip();
        return repository.findTop200ByOrderByOccurredAtDesc().stream()
                .map(AgentRegistryAuditJpaEntity::toDomain)
                .filter(event -> filter == null || filter.equals(event.agentId()))
                .limit(Math.max(1, Math.min(limit, 200)))
                .toList();
    }
}
