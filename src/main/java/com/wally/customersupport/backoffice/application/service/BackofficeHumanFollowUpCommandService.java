package com.wally.customersupport.backoffice.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.wally.customersupport.backoffice.application.model.BackofficeHumanFollowUpAction;
import com.wally.customersupport.conversation.application.port.out.HumanFollowUpTaskOperator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BackofficeHumanFollowUpCommandService {

    private final HumanFollowUpTaskOperator operator;
    private final Clock clock;

    @Transactional
    public BackofficeHumanFollowUpAction claim(UUID id, String actor) {
        requireActor(actor);
        ensureChanged(operator.claim(id, actor, Instant.now(clock)), "claim");
        return new BackofficeHumanFollowUpAction("claim", actor, "IN_PROGRESS");
    }

    @Transactional
    public BackofficeHumanFollowUpAction release(UUID id, String actor) {
        requireActor(actor);
        ensureChanged(operator.release(id, actor, Instant.now(clock)), "release");
        return new BackofficeHumanFollowUpAction("release", actor, "OPEN");
    }

    @Transactional
    public BackofficeHumanFollowUpAction resolve(UUID id, String actor) {
        requireActor(actor);
        ensureChanged(operator.resolve(id, actor, Instant.now(clock)), "resolve");
        return new BackofficeHumanFollowUpAction("resolve", actor, "DONE");
    }

    private static void requireActor(String actor) {
        if (actor == null || actor.isBlank() || actor.length() > 128) {
            throw new IllegalArgumentException("actor must be provided");
        }
    }

    private static void ensureChanged(boolean changed, String operation) {
        if (!changed) {
            throw new IllegalStateException("Follow-up task cannot be changed by operation: " + operation);
        }
    }
}
