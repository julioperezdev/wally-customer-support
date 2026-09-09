package com.wally.customersupport.conversation.application.port.out;

import java.util.Optional;

import com.wally.customersupport.conversation.domain.model.ContactSuppression;

public interface ContactSuppressionRepository {

    boolean existsActiveByActorKey(String actorKey);

    ContactSuppression saveIfAbsent(ContactSuppression suppression);

    Optional<ContactSuppression> findActiveByActorKey(String actorKey);
}
