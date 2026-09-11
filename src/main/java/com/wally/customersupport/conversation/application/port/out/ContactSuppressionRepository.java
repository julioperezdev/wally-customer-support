package com.wally.customersupport.conversation.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.wally.customersupport.conversation.domain.model.ContactSuppression;

public interface ContactSuppressionRepository {

    boolean existsActiveByActorKey(String actorKey);

    ContactSuppression saveIfAbsent(ContactSuppression suppression);

    boolean reactivate(String actorKey, Instant now);

    Optional<Instant> findLastReactivationAt(String actorKey);

    Optional<ContactSuppression> findActiveByActorKey(String actorKey);
}
