package com.wally.customersupport.conversation.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.ProcessingAttempt;

public interface ProcessingAttemptRepository {

    ProcessingAttempt save(ProcessingAttempt attempt);

    List<ProcessingAttempt> findDue(Instant now, int limit, Duration leaseDuration);

    boolean claim(UUID id, Instant now);

    void markCompleted(UUID id, Instant now);

    void markFailed(UUID id, String error, Instant availableAt, boolean exhausted, Instant now);
}
