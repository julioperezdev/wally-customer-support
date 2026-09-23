package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.agent.domain.model.AgentRegistryAuditEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_registry_audit_events", schema = "wcs")
public class AgentRegistryAuditJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String operation;

    @Column(name = "agent_id", nullable = false, length = 128)
    private String agentId;

    @Column(name = "agent_version")
    private Integer agentVersion;

    @Column(name = "previous_state", length = 32)
    private String previousState;

    @Column(name = "resulting_state", length = 32)
    private String resultingState;

    @Column(length = 64)
    private String environment;

    @Column(length = 32)
    private String channel;

    @Column(name = "use_case", length = 128)
    private String useCase;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "baseline_evaluation_run_id")
    private UUID baselineEvaluationRunId;

    @Column(name = "candidate_evaluation_run_id")
    private UUID candidateEvaluationRunId;

    @Column(name = "evaluation_dataset_version", length = 80)
    private String evaluationDatasetVersion;

    @Column(name = "evaluation_assessment_outcome", length = 40)
    private String evaluationAssessmentOutcome;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AgentRegistryAuditJpaEntity() {
    }

    public AgentRegistryAuditJpaEntity(AgentRegistryAuditEvent event) {
        this.id = UUID.randomUUID();
        this.operation = event.operation();
        this.agentId = event.agentId();
        this.agentVersion = event.agentVersion();
        this.previousState = event.previousState();
        this.resultingState = event.resultingState();
        this.environment = event.environment();
        this.channel = event.channel();
        this.useCase = event.useCase();
        this.actorId = event.actorId();
        this.reason = event.reason();
        this.occurredAt = event.occurredAt();
        this.baselineEvaluationRunId = event.baselineEvaluationRunId();
        this.candidateEvaluationRunId = event.candidateEvaluationRunId();
        this.evaluationDatasetVersion = event.evaluationDatasetVersion();
        this.evaluationAssessmentOutcome = event.evaluationAssessmentOutcome();
    }

    public AgentRegistryAuditEvent toDomain() {
        return new AgentRegistryAuditEvent(
                operation, agentId, agentVersion, previousState, resultingState,
                environment, channel, useCase, actorId, reason, occurredAt,
                baselineEvaluationRunId, candidateEvaluationRunId,
                evaluationDatasetVersion, evaluationAssessmentOutcome);
    }
}
