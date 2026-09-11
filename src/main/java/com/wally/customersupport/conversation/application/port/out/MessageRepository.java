package com.wally.customersupport.conversation.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.conversation.domain.model.Message;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.MessageWriteResult;

public interface MessageRepository {

    boolean existsByExternalMessageId(Channel channel, String externalMessageId);

    Optional<Message> findById(UUID id);

    MessageWriteResult saveIfAbsent(Message message);

    Message save(Message message);

    List<String> findRecentBodies(UUID conversationId, int limit);

    List<String> findRecentBodiesAfter(UUID conversationId, Instant occurredAfter, int limit);
}
