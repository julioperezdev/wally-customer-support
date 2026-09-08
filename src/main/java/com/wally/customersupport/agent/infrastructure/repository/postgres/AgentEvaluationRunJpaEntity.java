package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRun;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationRunSummary;
import com.wally.customersupport.agent.application.evaluation.AgentEvaluationOperationalMetrics;
import com.wally.customersupport.agent.domain.model.AgentEvaluationResult;
import com.wally.customersupport.agent.domain.model.AgentEvaluationSuiteResult;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_evaluation_runs", schema = "wcs")
public class AgentEvaluationRunJpaEntity {

    @Id
    private UUID id;

    @Column(name = "dataset_version", nullable = false, length = 80)
    private String datasetVersion;

    @Column(name = "agent_id", nullable = false, length = 128)
    private String agentId;

    @Column(name = "agent_version", nullable = false, length = 80)
    private String agentVersion;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(name = "model_id", nullable = false, length = 160)
    private String modelId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "total_scenarios", nullable = false)
    private int totalScenarios;

    @Column(name = "passed_scenarios", nullable = false)
    private int passedScenarios;

    @Column(name = "failed_scenarios", nullable = false)
    private int failedScenarios;

    @Column(name = "pass_rate", nullable = false, precision = 12, scale = 10)
    private BigDecimal passRate;

    @Column(name = "average_score", nullable = false, precision = 12, scale = 10)
    private BigDecimal averageScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failure_reasons", nullable = false, columnDefinition = "jsonb")
    private Map<String, Integer> failureReasons;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AgentEvaluationScenarioResultJpaEntity> scenarioResults = new ArrayList<>();

    protected AgentEvaluationRunJpaEntity() {
    }

    public AgentEvaluationRunJpaEntity(AgentEvaluationRun run) {
        this.id = run.runId();
        this.datasetVersion = run.datasetVersion();
        this.agentId = run.agentId();
        this.agentVersion = run.agentVersion();
        this.provider = run.provider();
        this.modelId = run.modelId();
        this.startedAt = run.startedAt();
        this.completedAt = run.completedAt();
        this.durationMs = run.durationMs();
        AgentEvaluationSuiteResult result = run.suiteResult();
        this.totalScenarios = result.totalScenarios();
        this.passedScenarios = result.passedScenarios();
        this.failedScenarios = result.failedScenarios();
        this.passRate = BigDecimal.valueOf(result.passRate());
        this.averageScore = BigDecimal.valueOf(result.averageScore());
        this.failureReasons = Map.copyOf(result.failureReasons());
        this.scenarioResults = result.scenarioResults().stream()
                .map(scenario -> new AgentEvaluationScenarioResultJpaEntity(this, scenario))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    public AgentEvaluationRun toDomain() {
        List<AgentEvaluationResult> results = scenarioResults.stream()
                .sorted(Comparator.comparing(AgentEvaluationScenarioResultJpaEntity::scenarioId))
                .map(AgentEvaluationScenarioResultJpaEntity::toDomain)
                .toList();
        AgentEvaluationSuiteResult suiteResult = new AgentEvaluationSuiteResult(
                datasetVersion,
                results,
                totalScenarios,
                passedScenarios,
                failedScenarios,
                passRate.doubleValue(),
                averageScore.doubleValue(),
                failureReasons);
        return new AgentEvaluationRun(
                id,
                datasetVersion,
                agentId,
                agentVersion,
                provider,
                modelId,
                startedAt,
                completedAt,
                durationMs,
                suiteResult);
    }

    public AgentEvaluationRunSummary toSummary() {
        List<AgentEvaluationResult> results = scenarioResults.stream()
                .map(AgentEvaluationScenarioResultJpaEntity::toDomain)
                .toList();
        AgentEvaluationOperationalMetrics operationalMetrics = AgentEvaluationOperationalMetrics.from(results);
        return new AgentEvaluationRunSummary(
                id,
                datasetVersion,
                agentId,
                agentVersion,
                provider,
                modelId,
                startedAt,
                completedAt,
                durationMs,
                totalScenarios,
                passedScenarios,
                failedScenarios,
                passRate.doubleValue(),
                averageScore.doubleValue(),
                failureReasons,
                operationalMetrics.totalTokens(),
                operationalMetrics.providerLatencyMs(),
                operationalMetrics.estimatedCostUsd());
    }
}
