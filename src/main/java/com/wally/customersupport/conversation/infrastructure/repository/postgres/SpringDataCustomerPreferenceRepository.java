package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCustomerPreferenceRepository
        extends JpaRepository<CustomerPreferenceJpaEntity, UUID> {

    List<CustomerPreferenceJpaEntity> findByActorId(String actorId);

    Optional<CustomerPreferenceJpaEntity> findByActorIdAndPreferenceKeyAndPreferenceScopeAndConversationIdIsNull(
            String actorId,
            String preferenceKey,
            String preferenceScope);

    Optional<CustomerPreferenceJpaEntity> findByActorIdAndConversationIdAndPreferenceKeyAndPreferenceScope(
            String actorId,
            UUID conversationId,
            String preferenceKey,
            String preferenceScope);

    void deleteByActorIdAndConversationId(String actorId, UUID conversationId);

    void deleteByActorId(String actorId);
}
