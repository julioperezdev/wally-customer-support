package com.wally.customersupport.conversation.infrastructure.memory.mock;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.wally.customersupport.conversation.application.port.out.CustomerPreferenceStore;
import com.wally.customersupport.conversation.domain.model.CustomerPreference;
import com.wally.customersupport.conversation.domain.model.PreferenceScope;

public class InMemoryCustomerPreferenceStore implements CustomerPreferenceStore {

    private final Map<PreferenceIdentity, CustomerPreference> preferences = new ConcurrentHashMap<>();

    @Override
    public List<CustomerPreference> findActive(String actorId, UUID conversationId, Instant now) {
        if (actorId == null || actorId.isBlank() || now == null) {
            return List.of();
        }
        return preferences.values().stream()
                .filter(preference -> preference.actorId().equals(actorId.strip()))
                .filter(preference -> preference.confirmed() && preference.expiresAt().isAfter(now))
                .filter(preference -> preference.scope() == PreferenceScope.ACTOR
                        || preference.conversationId().equals(conversationId))
                .toList();
    }

    @Override
    public CustomerPreference save(CustomerPreference preference) {
        preferences.put(PreferenceIdentity.from(preference), preference);
        return preference;
    }

    @Override
    public void clearConversation(UUID conversationId, String actorId) {
        if (conversationId == null || actorId == null) {
            return;
        }
        preferences.entrySet().removeIf(entry -> entry.getValue().actorId().equals(actorId.strip())
                && entry.getValue().scope() == PreferenceScope.CONVERSATION
                && conversationId.equals(entry.getValue().conversationId()));
    }

    @Override
    public void clearActor(String actorId) {
        if (actorId != null) {
            preferences.entrySet().removeIf(entry -> entry.getValue().actorId().equals(actorId.strip()));
        }
    }

    private record PreferenceIdentity(
            String actorId,
            UUID conversationId,
            String key,
            PreferenceScope scope) {

        private static PreferenceIdentity from(CustomerPreference preference) {
            return new PreferenceIdentity(
                    preference.actorId(), preference.conversationId(), preference.key(), preference.scope());
        }
    }
}
