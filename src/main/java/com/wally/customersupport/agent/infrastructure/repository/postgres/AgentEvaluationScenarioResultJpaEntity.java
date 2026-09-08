package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.agent.domain.model.AgentEvaluationExecutionMetadata;
import com.wally.customersupport.agent.domain.model.AgentEvaluationResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_evaluation_scenario_results", schema = "wcs")
public class AgentEvaluationScenarioResultJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private AgentEvaluationRunJpaEntity run;

    @Column(name = "scenario_id", nullable = false, length = 128)
    private String scenarioId;

    @Column(name = "dataset_version", nullable = false, length = 80)
    private String datasetVersion;

    @Column(nullable = false)
    private boolean passed;

    @Column(nullable = false, precision = 12, scale = 10)
    private BigDecimal score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failure_reasons", nullable = false, columnDefinition = "jsonb")
    private List<String> failureReasons;

    @Column(name = "execution_agent_id", length = 128)
    private String executionAgentId;

    @Column(name = "execution_agent_version", length = 80)
    private String executionAgentVersion;

    @Column(name = "execution_provider", length = 64)
    private String executionProvider;

    @Column(name = "execution_model_id", length = 160)
    private String executionModelId;

    @Column(name = "execution_duration_ms")
    private Long executionDurationMs;

    @Column(name = "provider_latency_ms")
    private Long providerLatencyMs;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "estimated_cost_usd", precision = 12, scale = 6)
    private BigDecimal estimatedCostUsd;

    @Column(name = "pricing_version", length = 80)
    private String pricingVersion;

    protected AgentEvaluationScenarioResultJpaEntity() {
    }

    public AgentEvaluationScenarioResultJpaEntity(
            AgentEvaluationRunJpaEntity run,
            AgentEvaluationResult result) {
        this.id = UUID.randomUUID();
        this.run = run;
        this.scenarioId = result.scenarioId();
        this.datasetVersion = result.datasetVersion();
        this.passed = result.passed();
        this.score = BigDecimal.valueOf(result.score());
        this.failureReasons = List.copyOf(result.reasons());
        AgentEvaluationExecutionMetadata metadata = result.executionMetadata();
        if (metadata != null) {
            this.executionAgentId = metadata.agentId();
            this.executionAgentVersion = metadata.agentVersion();
            this.executionProvider = metadata.provider();
            this.executionModelId = metadata.modelId();
            this.executionDurationMs = metadata.durationMs();
            this.providerLatencyMs = metadata.providerLatencyMs();
            this.inputTokens = metadata.inputTokens();
            this.outputTokens = metadata.outputTokens();
            this.totalTokens = metadata.totalTokens();
            this.estimatedCostUsd = metadata.estimatedCostUsd();
            this.pricingVersion = metadata.pricingVersion();
        }
    }

    public String scenarioId() {
        return scenarioId;
    }

    public AgentEvaluationResult toDomain() {
        AgentEvaluationExecutionMetadata metadata = executionDurationMs == null
                ? null
                : new AgentEvaluationExecutionMetadata(
                        executionAgentId,
                        executionAgentVersion,
                        executionProvider,
                        executionModelId,
                        executionDurationMs,
                        providerLatencyMs,
                        inputTokens,
                        outputTokens,
                        totalTokens,
                        estimatedCostUsd,
                        pricingVersion);
        return new AgentEvaluationResult(
                scenarioId,
                datasetVersion,
                passed,
                score.doubleValue(),
                failureReasons,
                metadata);
    }
}
