package com.wally.customersupport.application.port.out;

import java.util.Optional;

import com.wally.customersupport.domain.model.Channel;
import com.wally.customersupport.domain.model.Conversation;

public interface ConversationRepository {

    Optional<Conversation> findByChannelAndExternalConversationId(
            Channel channel,
            String externalConversationId);

    Conversation save(Conversation conversation);
}
