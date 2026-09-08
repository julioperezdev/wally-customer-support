package com.wally.customersupport.conversation.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.Conversation;

public interface ConversationRepository {

    Optional<Conversation> findByChannelAndExternalConversationId(
            Channel channel,
            String externalConversationId);

    Optional<Conversation> findById(UUID id);

    Conversation findOrCreate(
            Channel channel,
            String externalConversationId,
            String externalCustomerId,
            java.time.Instant now);

    Conversation save(Conversation conversation);
}
