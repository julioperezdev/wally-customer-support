package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.out.CustomerPreferenceStore;
import com.wally.customersupport.conversation.domain.model.CustomerPreference;
import com.wally.customersupport.conversation.domain.model.PreferenceScope;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(
        name = "wcs.conversation.preferences.enabled",
        havingValue = "true",
        matchIfMissing = false)
@RequiredArgsConstructor
public class JpaCustomerPreferenceStore implements CustomerPreferenceStore {

    private final SpringDataCustomerPreferenceRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerPreference> findActive(String actorId, UUID conversationId, Instant now) {
        if (actorId == null || actorId.isBlank() || now == null) {
            return List.of();
        }
        return repository.findByActorId(actorId.strip()).stream()
                .map(CustomerPreferenceJpaEntity::toDomain)
                .filter(preference -> preference.confirmed() && preference.expiresAt().isAfter(now))
                .filter(preference -> preference.scope() == PreferenceScope.ACTOR
                        || preference.conversationId().equals(conversationId))
                .toList();
    }

    @Override
    @Transactional
    public CustomerPreference save(CustomerPreference preference) {
        CustomerPreferenceJpaEntity entity = findExisting(preference)
                .orElseGet(() -> new CustomerPreferenceJpaEntity(preference));
        entity.updateFrom(preference);
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    @Transactional
    public void clearConversation(UUID conversationId, String actorId) {
        if (conversationId != null && actorId != null && !actorId.isBlank()) {
            repository.deleteByActorIdAndConversationId(actorId.strip(), conversationId);
        }
    }

    @Override
    @Transactional
    public void clearActor(String actorId) {
        if (actorId != null && !actorId.isBlank()) {
            repository.deleteByActorId(actorId.strip());
        }
    }

    private java.util.Optional<CustomerPreferenceJpaEntity> findExisting(CustomerPreference preference) {
        if (preference.scope() == PreferenceScope.ACTOR) {
            return repository.findByActorIdAndPreferenceKeyAndPreferenceScopeAndConversationIdIsNull(
                    preference.actorId(), preference.key(), preference.scope().name());
        }
        return repository.findByActorIdAndConversationIdAndPreferenceKeyAndPreferenceScope(
                preference.actorId(), preference.conversationId(), preference.key(), preference.scope().name());
    }
}
