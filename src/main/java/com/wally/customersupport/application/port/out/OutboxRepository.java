package com.wally.customersupport.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.domain.model.OutboxMessage;

public interface OutboxRepository {

    OutboxMessage save(OutboxMessage message);

    List<OutboxMessage> findDue(Instant now, int limit);

    void markProcessing(UUID id);

    void markSent(UUID id, Instant sentAt);

    void markFailed(UUID id, String error, Instant availableAt, boolean exhausted);
}
