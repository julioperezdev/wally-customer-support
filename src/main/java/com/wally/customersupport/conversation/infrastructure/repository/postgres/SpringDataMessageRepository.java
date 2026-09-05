package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.util.List;
import java.util.UUID;

import com.wally.customersupport.conversation.infrastructure.repository.postgres.MessageJpaEntity;
import com.wally.customersupport.conversation.domain.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMessageRepository extends JpaRepository<MessageJpaEntity, UUID> {

    boolean existsByChannelAndExternalMessageId(Channel channel, String externalMessageId);

    List<MessageJpaEntity> findTop20ByConversationIdOrderByOccurredAtDesc(UUID conversationId);
}
