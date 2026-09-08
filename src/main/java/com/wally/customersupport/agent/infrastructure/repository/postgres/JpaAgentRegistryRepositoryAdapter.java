package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.util.List;
import java.util.Optional;

import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.domain.model.AgentActivation;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import com.wally.customersupport.agent.domain.model.AgentVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class JpaAgentRegistryRepositoryAdapter implements AgentRegistryRepository {

    private final SpringDataAgentVersionRepository versionRepository;
    private final SpringDataAgentActivationRepository activationRepository;

    @Override
    @Transactional
    public AgentVersion saveVersion(AgentVersion version) {
        if (versionRepository.existsByAgentIdAndAgentVersion(version.agentId(), version.version())) {
            throw new IllegalStateException("agent versions are immutable once persisted");
        }
        return versionRepository.saveAndFlush(new AgentVersionJpaEntity(version)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentVersion> findVersion(String agentId, int version) {
        return versionRepository.findByAgentIdAndAgentVersion(agentId, version)
                .map(AgentVersionJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentVersion> findVersions(String agentId) {
        return versionRepository.findByAgentIdOrderByAgentVersionDesc(agentId).stream()
                .map(AgentVersionJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentVersion> findAllVersions() {
        return versionRepository.findAllByOrderByAgentIdAscAgentVersionDesc().stream()
                .map(AgentVersionJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentVersion> findLatestVersion(String agentId, AgentLifecycleState state) {
        return versionRepository.findFirstByAgentIdAndStateOrderByAgentVersionDesc(agentId, state.name())
                .map(AgentVersionJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public AgentActivation saveActivation(AgentActivation activation) {
        return activationRepository.saveAndFlush(new AgentActivationJpaEntity(activation)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentActivation> findAllActivations() {
        return activationRepository.findAllByOrderByAgentIdAscActivatedAtDesc().stream()
                .map(AgentActivationJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentActivation> findLatestActivation(
            String agentId,
            String environment,
            String channel,
            String useCase) {
        return activationRepository
                .findFirstByAgentIdAndEnvironmentAndChannelAndUseCaseOrderByActivatedAtDesc(
                        agentId, environment, channel, useCase)
                .map(AgentActivationJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentActivation> findActiveActivation(
            String agentId,
            String environment,
            String channel,
            String useCase) {
        return findLatestActivation(agentId, environment, channel, useCase)
                .filter(activation -> activation.enabled() && !activation.killSwitch());
    }
}
