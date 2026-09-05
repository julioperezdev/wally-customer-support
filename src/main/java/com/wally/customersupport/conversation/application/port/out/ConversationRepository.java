package com.wally.customersupport.conversation.application.port.out;

import java.util.Optional;

import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.Conversation;

public interface ConversationRepository {

    Optional<Conversation> findByChannelAndExternalConversationId(
            Channel channel,
            String externalConversationId);

    Conversation save(Conversation conversation);
}
