package com.wally.customersupport.agent.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_activation_command_claims", schema = "wcs")
public class AgentActivationCommandClaimJpaEntity {

    @Id
    private UUID id;

    @Column(name = "key_hash", nullable = false, length = 64)
    private String keyHash;

    @Column(name = "claimed_at", nullable = false)
    private Instant claimedAt;

    protected AgentActivationCommandClaimJpaEntity() {
    }
}
