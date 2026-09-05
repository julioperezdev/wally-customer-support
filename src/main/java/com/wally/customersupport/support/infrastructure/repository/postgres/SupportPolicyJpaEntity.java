package com.wally.customersupport.support.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.support.domain.model.SupportPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "support_policies", schema = "wcs")
public class SupportPolicyJpaEntity {

    @Id
    private UUID id;

    @Column(name = "policy_key", nullable = false, length = 80)
    private String policyKey;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private boolean demo;

    @Column(name = "record_version", nullable = false)
    private int version;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SupportPolicyJpaEntity() {
    }

    public SupportPolicy toDomain() {
        return new SupportPolicy(id, policyKey, title, content, active, demo, version, publishedAt);
    }
}
