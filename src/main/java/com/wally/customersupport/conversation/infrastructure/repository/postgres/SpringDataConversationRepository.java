package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.conversation.infrastructure.repository.postgres.ConversationJpaEntity;
import com.wally.customersupport.conversation.domain.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataConversationRepository extends JpaRepository<ConversationJpaEntity, UUID> {

    Optional<ConversationJpaEntity> findByChannelAndExternalConversationId(
            Channel channel,
            String externalConversationId);
}
