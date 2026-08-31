package com.wally.customersupport.adapter.out.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.adapter.out.persistence.entity.ConversationJpaEntity;
import com.wally.customersupport.domain.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataConversationRepository extends JpaRepository<ConversationJpaEntity, UUID> {

    Optional<ConversationJpaEntity> findByChannelAndExternalConversationId(
            Channel channel,
            String externalConversationId);
}
