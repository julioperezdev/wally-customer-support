package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.agent.domain.model.AgentActivation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_activations", schema = "wcs")
public class AgentActivationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "agent_id", nullable = false, length = 128)
    private String agentId;

    @Column(name = "agent_version", nullable = false)
    private int agentVersion;

    @Column(nullable = false, length = 64)
    private String environment;

    @Column(nullable = false, length = 32)
    private String channel;

    @Column(name = "use_case", nullable = false, length = 128)
    private String useCase;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "rollout_percentage", nullable = false)
    private int rolloutPercentage;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "kill_switch", nullable = false)
    private boolean killSwitch;

    @Column(name = "previous_version")
    private Integer previousVersion;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    @Column(name = "activated_by", nullable = false, length = 128)
    private String activatedBy;

    protected AgentActivationJpaEntity() {
    }

    public AgentActivationJpaEntity(AgentActivation activation) {
        this.id = UUID.randomUUID();
        this.agentId = activation.agentId();
        this.agentVersion = activation.agentVersion();
        this.environment = activation.environment();
        this.channel = activation.channel();
        this.useCase = activation.useCase();
        this.reason = activation.reason();
        this.rolloutPercentage = activation.rolloutPercentage();
        this.enabled = activation.enabled();
        this.killSwitch = activation.killSwitch();
        this.previousVersion = activation.previousVersion();
        this.activatedAt = activation.activatedAt();
        this.activatedBy = activation.activatedBy();
    }

    public AgentActivation toDomain() {
        return new AgentActivation(
                agentId,
                agentVersion,
                environment,
                channel,
                useCase,
                reason,
                rolloutPercentage,
                enabled,
                killSwitch,
                previousVersion,
                activatedAt,
                activatedBy);
    }
}
