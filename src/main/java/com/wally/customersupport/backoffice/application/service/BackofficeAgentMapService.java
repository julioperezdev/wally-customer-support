package com.wally.customersupport.backoffice.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryFilter;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPage;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationHistoryPageRequest;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRunSummary;
import com.wally.customersupport.agent.application.registry.AgentRegistryActivationView;
import com.wally.customersupport.agent.application.registry.AgentRegistryAgentView;
import com.wally.customersupport.agent.application.registry.AgentRegistryQuery;
import com.wally.customersupport.agent.application.registry.AgentRegistryVersionView;
import com.wally.customersupport.agent.application.service.AgentEvaluationHistoryQueryService;
import com.wally.customersupport.agent.application.service.AgentRegistryQueryService;
import com.wally.customersupport.backoffice.application.model.BackofficeAgentEdge;
import com.wally.customersupport.backoffice.application.model.BackofficeAgentMap;
import com.wally.customersupport.backoffice.application.model.BackofficeAgentMapQuery;
import com.wally.customersupport.backoffice.application.model.BackofficeAgentMapSimulation;
import com.wally.customersupport.backoffice.application.model.BackofficeAgentMapSimulationRequest;
import com.wally.customersupport.backoffice.application.model.BackofficeAgentNode;
import com.wally.customersupport.backoffice.application.model.BackofficeUseCaseMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only projection of the agent registry and its sanitized evaluation evidence.
 *
 * <p>The first operational slice deliberately uses evaluation runs as the metric
 * source. It does not present them as production traffic: live execution metrics
 * will be added when the runtime trace store is available.</p>
 */
@Service
@RequiredArgsConstructor
public class BackofficeAgentMapService {

    private static final int MAX_REGISTRY_AGENTS = 100;
    private static final int MAX_EVIDENCE_RUNS = 100;

    private final AgentRegistryQueryService registryQueryService;
    private final AgentEvaluationHistoryQueryService historyQueryService;

    @Transactional(readOnly = true)
    public BackofficeAgentMap describe(BackofficeAgentMapQuery query) {
        BackofficeAgentMapQuery mapQuery = Objects.requireNonNull(query, "query");
        List<AgentRegistryAgentView> agents = registryQueryService.search(new AgentRegistryQuery(
                mapQuery.agentId(), mapQuery.environment(), mapQuery.channel(), mapQuery.useCase(),
                MAX_REGISTRY_AGENTS));
        Evidence evidence = loadEvidence();
        Map<String, List<AgentRegistryActivationView>> activationsByUseCase = activationsByUseCase(agents, mapQuery);
        List<BackofficeUseCaseMap> useCases = activationsByUseCase.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> toUseCaseMap(mapQuery, entry.getKey(), agents, evidence))
                .filter(map -> !map.agents().isEmpty())
                .toList();
        return new BackofficeAgentMap(
                Instant.now(), mapQuery, useCases, evidence.totalRuns(), evidence.truncated());
    }

    @Transactional(readOnly = true)
    public BackofficeAgentMapSimulation simulate(BackofficeAgentMapSimulationRequest request) {
        BackofficeAgentMapSimulationRequest simulationRequest = Objects.requireNonNull(request, "request");
        BackofficeAgentMap map = describe(simulationRequest.toQuery());
        Optional<BackofficeAgentNode> disabled = map.useCases().stream()
                .flatMap(useCase -> useCase.agents().stream())
                .filter(agent -> agent.agentId().equals(simulationRequest.disabledAgentId()))
                .filter(agent -> simulationRequest.disabledVersion() == null
                        || agent.version() == simulationRequest.disabledVersion())
                .filter(agent -> "ACTIVE".equals(agent.status()))
                .findFirst();

        if (disabled.isEmpty()) {
            return new BackofficeAgentMapSimulation(
                    simulationRequest.environment(), simulationRequest.channel(), simulationRequest.useCase(),
                    simulationRequest.disabledAgentId(), simulationRequest.disabledVersion(), false,
                    "NO_CHANGE", "El agente no es la activación activa de este caso de uso.", List.of());
        }

        BackofficeAgentNode disabledAgent = disabled.get();
        Optional<BackofficeAgentNode> fallback = findActiveFallback(map, disabledAgent);
        if (fallback.isPresent()) {
            return new BackofficeAgentMapSimulation(
                    simulationRequest.environment(), simulationRequest.channel(), simulationRequest.useCase(),
                    simulationRequest.disabledAgentId(), simulationRequest.disabledVersion(), true,
                    "FALLBACK_AGENT", "La desactivación resolvería al fallback compatible registrado.",
                    List.of(
                            new BackofficeAgentMapSimulation.BackofficeRouteStep(
                                    "DISABLED", disabledAgent.agentId(), disabledAgent.version(), "simulated deactivation"),
                            new BackofficeAgentMapSimulation.BackofficeRouteStep(
                                    "FALLBACK", fallback.get().agentId(), fallback.get().version(), "configured fallback")));
        }

        return new BackofficeAgentMapSimulation(
                simulationRequest.environment(), simulationRequest.channel(), simulationRequest.useCase(),
                simulationRequest.disabledAgentId(), simulationRequest.disabledVersion(), true,
                "HUMAN_REQUIRED", "No hay fallback activo compatible; la ruta debe derivar a un agente humano.",
                List.of(
                        new BackofficeAgentMapSimulation.BackofficeRouteStep(
                                "DISABLED", disabledAgent.agentId(), disabledAgent.version(), "simulated deactivation"),
                        new BackofficeAgentMapSimulation.BackofficeRouteStep(
                                "HUMAN", null, null, "no compatible fallback")));
    }

    private BackofficeUseCaseMap toUseCaseMap(
            BackofficeAgentMapQuery query,
            String useCase,
            List<AgentRegistryAgentView> agents,
            Evidence evidence) {
        List<BackofficeAgentNode> nodes = agents.stream()
                .map(agent -> activeActivation(agent, query.environment(), query.channel(), useCase)
                        .map(activation -> toNode(agent, activation.activation(), activation.version(), evidence.metrics(), Instant.now())))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(BackofficeAgentNode::agentId))
                .toList();
        List<BackofficeAgentEdge> edges = new ArrayList<>();
        for (BackofficeAgentNode node : nodes) {
            String source = node.agentId() + ":v" + node.version();
            edges.add(new BackofficeAgentEdge("use-case:" + useCase, "ROUTES_TO", source));
            node.allowedTools().forEach(tool -> edges.add(new BackofficeAgentEdge(source, "USES_TOOL", "tool:" + tool)));
            node.knowledgeSources().forEach(sourceId ->
                    edges.add(new BackofficeAgentEdge(source, "USES_KNOWLEDGE_SOURCE", "knowledge:" + sourceId)));
            if (node.fallbackAgentId() != null) {
                edges.add(new BackofficeAgentEdge(source, "FALLBACK_TO", "agent:" + node.fallbackAgentId()));
            } else {
                edges.add(new BackofficeAgentEdge(source, "HANDOFF_TO_HUMAN", "human"));
            }
        }
        return new BackofficeUseCaseMap(query.environment(), query.channel(), useCase, nodes, edges);
    }

    private static Optional<ActivationAndVersion> activeActivation(
            AgentRegistryAgentView agent,
            String environment,
            String channel,
            String useCase) {
        return agent.activations().stream()
                .filter(activation -> activation.environment().equals(environment))
                .filter(activation -> channel == null || activation.channel().equals(channel))
                .filter(activation -> activation.useCase().equals(useCase))
                .max(Comparator.comparing(AgentRegistryActivationView::activatedAt))
                .flatMap(activation -> agent.versions().stream()
                        .filter(version -> version.version() == activation.agentVersion())
                        .findFirst()
                        .map(version -> new ActivationAndVersion(activation, version)));
    }

    private static Optional<BackofficeAgentNode> findActiveFallback(
            BackofficeAgentMap map,
            BackofficeAgentNode disabled) {
        if (disabled.fallbackAgentId() == null) {
            return Optional.empty();
        }
        return map.useCases().stream()
                .filter(useCase -> useCase.useCase().equals(map.filters().useCase()))
                .flatMap(useCase -> useCase.agents().stream())
                .filter(agent -> agent.agentId().equals(disabled.fallbackAgentId()))
                .filter(agent -> "ACTIVE".equals(agent.status()))
                .findFirst();
    }

    private static Map<String, List<AgentRegistryActivationView>> activationsByUseCase(
            List<AgentRegistryAgentView> agents,
            BackofficeAgentMapQuery query) {
        return agents.stream()
                .flatMap(agent -> agent.activations().stream())
                .filter(activation -> activation.environment().equals(query.environment()))
                .filter(activation -> query.channel() == null || activation.channel().equals(query.channel()))
                .filter(activation -> query.useCase() == null || activation.useCase().equals(query.useCase()))
                .collect(Collectors.groupingBy(
                        AgentRegistryActivationView::useCase,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    private Evidence loadEvidence() {
        AgentEvaluationHistoryPage page = historyQueryService.search(
                AgentEvaluationHistoryFilter.all(),
                new AgentEvaluationHistoryPageRequest(0, MAX_EVIDENCE_RUNS));
        Map<String, Metrics> metrics = new LinkedHashMap<>();
        page.items().forEach(run -> metrics.computeIfAbsent(key(run.agentId(), run.agentVersion()), ignored -> new Metrics())
                .add(run));
        return new Evidence(metrics, page.totalElements(), page.totalElements() > page.items().size());
    }

    private static BackofficeAgentNode toNode(
            AgentRegistryAgentView agent,
            AgentRegistryActivationView activation,
            AgentRegistryVersionView version,
            Map<String, Metrics> metrics,
            Instant now) {
        Metrics metric = metrics.getOrDefault(key(agent.agentId(), Integer.toString(version.version())), Metrics.empty());
        long ageDays = Math.max(0, Duration.between(version.createdAt(), now).toDays());
        String status = activation.killSwitch() ? "KILL_SWITCH" : activation.enabled() ? "ACTIVE" : "INACTIVE";
        return new BackofficeAgentNode(
                agent.agentId(), version.version(), version.name(), version.purpose(), version.state().name(), status,
                version.modelProvider(), version.modelId(), version.createdAt(), ageDays, activation.enabled(),
                activation.killSwitch(), activation.rolloutPercentage(), metric.executionCount, metric.successCount,
                metric.failureCount, metric.successRate(), metric.averageLatencyMs(), metric.totalTokens(),
                metric.estimatedCostUsd(), version.fallbackAgentId(), version.allowedTools().stream().sorted().toList(),
                version.knowledgeSources().stream().sorted().toList(), "EVALUATION_RUNS");
    }

    private record ActivationAndVersion(
            AgentRegistryActivationView activation,
            AgentRegistryVersionView version) {
    }

    private static String key(String agentId, String version) {
        return agentId + "\u0000" + version;
    }

    private record Evidence(Map<String, Metrics> metrics, long totalRuns, boolean truncated) {
    }

    private static final class Metrics {
        private long executionCount;
        private long successCount;
        private long failureCount;
        private long latencyTotal;
        private long latencySamples;
        private long tokenTotal;
        private boolean hasTokens;
        private BigDecimal costTotal = BigDecimal.ZERO;
        private boolean hasCost;

        private void add(AgentEvaluationRunSummary run) {
            executionCount++;
            if (run.failedScenarios() == 0) {
                successCount++;
            } else {
                failureCount++;
            }
            if (run.durationMs() >= 0) {
                latencyTotal += run.durationMs();
                latencySamples++;
            }
            if (run.totalTokens() != null) {
                tokenTotal += run.totalTokens();
                hasTokens = true;
            }
            if (run.estimatedCostUsd() != null) {
                costTotal = costTotal.add(run.estimatedCostUsd());
                hasCost = true;
            }
        }

        private double successRate() {
            return executionCount == 0 ? 0 : (double) successCount / executionCount;
        }

        private Long averageLatencyMs() {
            return latencySamples == 0 ? null : latencyTotal / latencySamples;
        }

        private Long totalTokens() {
            return hasTokens ? tokenTotal : null;
        }

        private BigDecimal estimatedCostUsd() {
            return hasCost ? costTotal.setScale(6, RoundingMode.HALF_UP) : null;
        }

        private static Metrics empty() {
            return new Metrics();
        }
    }
}
