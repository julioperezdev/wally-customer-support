package com.wally.customersupport.conversation.application.port.out;

import java.time.Instant;

public interface ConversationRetentionRepository {

    int redactMessageBodiesBefore(Instant cutoff, String replacement, int limit);

    int deleteMessagesBefore(Instant cutoff, int limit);
}
