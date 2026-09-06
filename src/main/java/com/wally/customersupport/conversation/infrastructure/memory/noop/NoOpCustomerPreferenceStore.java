package com.wally.customersupport.conversation.infrastructure.memory.noop;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.out.CustomerPreferenceStore;
import com.wally.customersupport.conversation.domain.model.CustomerPreference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(
        name = "wcs.conversation.preferences.enabled",
        havingValue = "false",
        matchIfMissing = true)
public class NoOpCustomerPreferenceStore implements CustomerPreferenceStore {

    @Override
    public List<CustomerPreference> findActive(String actorId, UUID conversationId, Instant now) {
        return List.of();
    }

    @Override
    public CustomerPreference save(CustomerPreference preference) {
        return preference;
    }

    @Override
    public void clearConversation(UUID conversationId, String actorId) {
        // Preferences are disabled; there is no state to clear.
    }

    @Override
    public void clearActor(String actorId) {
        // Preferences are disabled; there is no state to clear.
    }
}
