package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.CustomerPreference;
import com.wally.customersupport.conversation.domain.model.PreferenceOrigin;
import com.wally.customersupport.conversation.domain.model.PreferenceScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "customer_preferences", schema = "wcs")
public class CustomerPreferenceJpaEntity {

    @Id
    private UUID id;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "actor_id", nullable = false, length = 128)
    private String actorId;

    @Column(name = "preference_key", nullable = false, length = 64)
    private String preferenceKey;

    @Column(name = "preference_value", nullable = false, length = 128)
    private String preferenceValue;

    @Column(name = "preference_scope", nullable = false, length = 32)
    private String preferenceScope;

    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column(nullable = false, length = 32)
    private String origin;

    @Column(nullable = false)
    private boolean confirmed;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected CustomerPreferenceJpaEntity() {
    }

    public CustomerPreferenceJpaEntity(CustomerPreference preference) {
        this.id = UUID.randomUUID();
        updateFrom(preference);
    }

    public void updateFrom(CustomerPreference preference) {
        this.conversationId = preference.conversationId();
        this.actorId = preference.actorId();
        this.preferenceKey = preference.key();
        this.preferenceValue = preference.value();
        this.preferenceScope = preference.scope().name();
        this.confidence = BigDecimal.valueOf(preference.confidence());
        this.origin = preference.origin().name();
        this.confirmed = preference.confirmed();
        this.updatedAt = preference.updatedAt();
        this.expiresAt = preference.expiresAt();
    }

    public CustomerPreference toDomain() {
        return new CustomerPreference(
                conversationId,
                actorId,
                preferenceKey,
                preferenceValue,
                PreferenceScope.valueOf(preferenceScope),
                confidence.doubleValue(),
                PreferenceOrigin.valueOf(origin),
                confirmed,
                updatedAt,
                expiresAt);
    }

    public UUID conversationId() {
        return conversationId;
    }

    public String actorId() {
        return actorId;
    }

    public String preferenceKey() {
        return preferenceKey;
    }

    public String preferenceScope() {
        return preferenceScope;
    }
}
