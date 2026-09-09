package com.wally.customersupport.conversation.application.port.out;

import java.time.Instant;
import java.util.UUID;

public interface HumanFollowUpTaskOperator {

    boolean claim(UUID id, String actor, Instant now);

    boolean release(UUID id, String actor, Instant now);

    boolean resolve(UUID id, String actor, Instant now);
}
