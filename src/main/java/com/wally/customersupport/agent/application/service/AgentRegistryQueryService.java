package com.wally.customersupport.agent.application.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.wally.customersupport.agent.application.port.out.AgentRegistryRepository;
import com.wally.customersupport.agent.application.registry.AgentRegistryActivationView;
import com.wally.customersupport.agent.application.registry.AgentRegistryAgentView;
import com.wally.customersupport.agent.application.registry.AgentRegistryQuery;
import com.wally.customersupport.agent.application.registry.AgentRegistryVersionView;
import com.wally.customersupport.agent.domain.model.AgentActivation;
import com.wally.customersupport.agent.domain.model.AgentVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds a bounded read model from the registry port without exposing persistence details. */
@Service
@RequiredArgsConstructor
public class AgentRegistryQueryService {

    private final AgentRegistryRepository registryRepository;

    @Transactional(readOnly = true)
    public List<AgentRegistryAgentView> search(AgentRegistryQuery query) {
        AgentRegistryQuery registryQuery = Objects.requireNonNull(query, "query");
        Map<String, List<AgentVersion>> versionsByAgent = new LinkedHashMap<>();
        registryRepository.findAllVersions().stream()
                .filter(version -> registryQuery.matchesAgent(version.agentId()))
                .sorted(Comparator.comparing(AgentVersion::agentId)
                        .thenComparing(AgentVersion::version, Comparator.reverseOrder()))
                .forEach(version -> versionsByAgent.computeIfAbsent(version.agentId(), ignored -> new ArrayList<>())
                        .add(version));

        Map<String, List<AgentActivation>> activationsByAgent = new LinkedHashMap<>();
        registryRepository.findAllActivations().stream()
                .filter(activation -> registryQuery.matchesAgent(activation.agentId()))
                .filter(activation -> registryQuery.matchesActivation(
                        activation.environment(), activation.channel(), activation.useCase()))
                .sorted(Comparator.comparing(AgentActivation::agentId)
                        .thenComparing(AgentActivation::activatedAt, Comparator.reverseOrder()))
                .forEach(activation -> activationsByAgent
                        .computeIfAbsent(activation.agentId(), ignored -> new ArrayList<>())
                        .add(activation));

        return versionsByAgent.keySet().stream()
                .filter(agentId -> !hasActivationFilters(registryQuery)
                        || !activationsByAgent.getOrDefault(agentId, List.of()).isEmpty())
                .sorted()
                .limit(registryQuery.limit())
                .map(agentId -> new AgentRegistryAgentView(
                        agentId,
                        versionsByAgent.getOrDefault(agentId, List.of()).stream()
                                .map(AgentRegistryQueryService::toVersionView)
                                .toList(),
                        activationsByAgent.getOrDefault(agentId, List.of()).stream()
                                .map(AgentRegistryQueryService::toActivationView)
                                .toList()))
                .toList();
    }

    private static boolean hasActivationFilters(AgentRegistryQuery query) {
        return query.environment() != null || query.channel() != null || query.useCase() != null;
    }

    private static AgentRegistryVersionView toVersionView(AgentVersion version) {
        return new AgentRegistryVersionView(
                version.agentId(),
                version.version(),
                version.name(),
                version.purpose(),
                version.state(),
                version.modelProvider(),
                version.modelId(),
                version.inferenceParameters().temperature(),
                version.inferenceParameters().topP(),
                version.systemPromptVersion(),
                version.systemPromptHash(),
                version.inputSchemaVersion(),
                version.outputSchemaVersion(),
                version.allowedTools(),
                version.knowledgeSources(),
                version.memoryPolicy(),
                version.responsePolicy(),
                version.timeout().toMillis(),
                version.maxSteps(),
                version.maxInputTokens(),
                version.maxOutputTokens(),
                version.budgetLimitUsd(),
                version.fallbackAgentId(),
                version.evaluationSuiteVersion(),
                version.createdAt(),
                version.approvedAt());
    }

    private static AgentRegistryActivationView toActivationView(AgentActivation activation) {
        return new AgentRegistryActivationView(
                activation.agentId(),
                activation.agentVersion(),
                activation.environment(),
                activation.channel(),
                activation.useCase(),
                activation.reason(),
                activation.rolloutPercentage(),
                activation.enabled(),
                activation.killSwitch(),
                activation.previousVersion(),
                activation.activatedAt());
    }
}
