package com.wally.customersupport.conversation.application.port.out;

import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.Message;
import com.wally.customersupport.conversation.domain.model.Channel;

public interface MessageRepository {

    boolean existsByExternalMessageId(Channel channel, String externalMessageId);

    Message save(Message message);

    List<String> findRecentBodies(UUID conversationId, int limit);
}
