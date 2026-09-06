package com.wally.customersupport.conversation.infrastructure.memory.noop;

import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.conversation.application.port.out.ConversationMemory;
import com.wally.customersupport.conversation.domain.model.ConversationState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Keeps the application usable while conversational memory is not activated.
 *
 * <p>The PostgreSQL adapter is enabled explicitly after the retention policy and
 * operational controls have been approved. The no-op implementation makes that
 * activation reversible through configuration and avoids making memory storage a
 * prerequisite for the channel flows.</p>
 */
@Component
@ConditionalOnMissingBean(ConversationMemory.class)
public class NoOpConversationMemoryAdapter implements ConversationMemory {

    @Override
    public Optional<ConversationState> load(UUID conversationId, String actorId) {
        return Optional.empty();
    }

    @Override
    public ConversationState save(ConversationState state) {
        return state;
    }

    @Override
    public void clear(UUID conversationId, String actorId) {
        // No state is retained while memory is disabled.
    }
}
