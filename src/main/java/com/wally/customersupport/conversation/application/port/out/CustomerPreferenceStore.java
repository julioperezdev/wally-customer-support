package com.wally.customersupport.conversation.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.CustomerPreference;

/** Storage-neutral boundary for explicitly confirmed, low-risk preferences. */
public interface CustomerPreferenceStore {

    List<CustomerPreference> findActive(String actorId, UUID conversationId, Instant now);

    CustomerPreference save(CustomerPreference preference);

    void clearConversation(UUID conversationId, String actorId);

    void clearActor(String actorId);
}
