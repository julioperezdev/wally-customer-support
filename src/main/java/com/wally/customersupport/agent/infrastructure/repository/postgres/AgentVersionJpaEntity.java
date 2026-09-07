package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.wally.customersupport.agent.domain.model.AgentInferenceParameters;
import com.wally.customersupport.agent.domain.model.AgentLifecycleState;
import com.wally.customersupport.agent.domain.model.AgentVersion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_versions", schema = "wcs")
public class AgentVersionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "agent_id", nullable = false, length = 128)
    private String agentId;

    @Column(name = "agent_version", nullable = false)
    private int agentVersion;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 1000)
    private String purpose;

    @Column(nullable = false, length = 32)
    private String state;

    @Column(name = "model_provider", nullable = false, length = 64)
    private String modelProvider;

    @Column(name = "model_id", nullable = false, length = 160)
    private String modelId;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal temperature;

    @Column(name = "top_p", nullable = false, precision = 4, scale = 3)
    private BigDecimal topP;

    @Column(name = "system_prompt_version", nullable = false, length = 80)
    private String systemPromptVersion;

    @Column(name = "system_prompt_hash", nullable = false, length = 64)
    private String systemPromptHash;

    @Column(name = "input_schema_version", nullable = false, length = 80)
    private String inputSchemaVersion;

    @Column(name = "output_schema_version", nullable = false, length = 80)
    private String outputSchemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allowed_tools", nullable = false, columnDefinition = "jsonb")
    private Set<String> allowedTools;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "knowledge_sources", nullable = false, columnDefinition = "jsonb")
    private Set<String> knowledgeSources;

    @Column(name = "memory_policy", nullable = false, length = 160)
    private String memoryPolicy;

    @Column(name = "response_policy", nullable = false, length = 160)
    private String responsePolicy;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Column(name = "max_steps", nullable = false)
    private int maxSteps;

    @Column(name = "max_input_tokens", nullable = false)
    private int maxInputTokens;

    @Column(name = "max_output_tokens", nullable = false)
    private int maxOutputTokens;

    @Column(name = "budget_limit_usd", nullable = false, precision = 12, scale = 6)
    private BigDecimal budgetLimitUsd;

    @Column(name = "fallback_agent_id", length = 128)
    private String fallbackAgentId;

    @Column(name = "evaluation_suite_version", nullable = false, length = 80)
    private String evaluationSuiteVersion;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "approved_by", length = 128)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected AgentVersionJpaEntity() {
    }

    public AgentVersionJpaEntity(AgentVersion version) {
        this.id = UUID.randomUUID();
        this.agentId = version.agentId();
        this.agentVersion = version.version();
        this.name = version.name();
        this.purpose = version.purpose();
        this.state = version.state().name();
        this.modelProvider = version.modelProvider();
        this.modelId = version.modelId();
        this.temperature = version.inferenceParameters().temperature();
        this.topP = version.inferenceParameters().topP();
        this.systemPromptVersion = version.systemPromptVersion();
        this.systemPromptHash = version.systemPromptHash();
        this.inputSchemaVersion = version.inputSchemaVersion();
        this.outputSchemaVersion = version.outputSchemaVersion();
        this.allowedTools = Set.copyOf(version.allowedTools());
        this.knowledgeSources = Set.copyOf(version.knowledgeSources());
        this.memoryPolicy = version.memoryPolicy();
        this.responsePolicy = version.responsePolicy();
        this.timeoutMs = Math.toIntExact(version.timeout().toMillis());
        this.maxSteps = version.maxSteps();
        this.maxInputTokens = version.maxInputTokens();
        this.maxOutputTokens = version.maxOutputTokens();
        this.budgetLimitUsd = version.budgetLimitUsd();
        this.fallbackAgentId = version.fallbackAgentId();
        this.evaluationSuiteVersion = version.evaluationSuiteVersion();
        this.createdBy = version.createdBy();
        this.createdAt = version.createdAt();
        this.approvedBy = version.approvedBy();
        this.approvedAt = version.approvedAt();
    }

    public AgentVersion toDomain() {
        return new AgentVersion(
                agentId,
                agentVersion,
                name,
                purpose,
                AgentLifecycleState.valueOf(state),
                modelProvider,
                modelId,
                new AgentInferenceParameters(temperature, topP),
                systemPromptVersion,
                systemPromptHash,
                inputSchemaVersion,
                outputSchemaVersion,
                allowedTools,
                knowledgeSources,
                memoryPolicy,
                responsePolicy,
                Duration.ofMillis(timeoutMs),
                maxSteps,
                maxInputTokens,
                maxOutputTokens,
                budgetLimitUsd,
                fallbackAgentId,
                evaluationSuiteVersion,
                createdBy,
                createdAt,
                approvedBy,
                approvedAt);
    }
}
