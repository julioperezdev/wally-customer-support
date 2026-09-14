package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.agent.domain.model.AgentExecutionTrace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_execution_traces", schema = "wcs")
public class AgentExecutionTraceJpaEntity {

    @Id
    private UUID id;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "actor_key", length = 128)
    private String actorKey;

    @Column(name = "agent_id", length = 128)
    private String agentId;

    @Column(name = "agent_version")
    private Integer agentVersion;

    @Column(nullable = false, length = 64)
    private String environment;

    @Column(nullable = false, length = 32)
    private String channel;

    @Column(name = "use_case", nullable = false, length = 128)
    private String useCase;

    @Column(nullable = false, length = 32)
    private String outcome;

    @Column(name = "resolution_status", nullable = false, length = 32)
    private String resolutionStatus;

    @Column(length = 64)
    private String provider;

    @Column(name = "model_id", length = 160)
    private String modelId;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "estimated_cost_usd", precision = 12, scale = 8)
    private BigDecimal estimatedCostUsd;

    @Column(name = "error_type", length = 160)
    private String errorType;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    protected AgentExecutionTraceJpaEntity() {
    }

    public AgentExecutionTraceJpaEntity(AgentExecutionTrace trace) {
        this.id = trace.traceId();
        this.correlationId = trace.correlationId();
        this.actorKey = trace.actorKey();
        this.agentId = trace.agentId();
        this.agentVersion = trace.agentVersion();
        this.environment = trace.environment();
        this.channel = trace.channel();
        this.useCase = trace.useCase();
        this.outcome = trace.outcome();
        this.resolutionStatus = trace.resolutionStatus();
        this.provider = trace.provider();
        this.modelId = trace.modelId();
        this.durationMs = trace.durationMs();
        this.inputTokens = trace.inputTokens();
        this.outputTokens = trace.outputTokens();
        this.estimatedCostUsd = trace.estimatedCostUsd();
        this.errorType = trace.errorType();
        this.executedAt = trace.executedAt();
    }

    public AgentExecutionTrace toDomain() {
        return new AgentExecutionTrace(
                id, correlationId, actorKey, agentId, agentVersion, environment, channel,
                useCase, outcome, resolutionStatus, provider, modelId, durationMs,
                inputTokens, outputTokens, estimatedCostUsd, errorType, executedAt);
    }
}
