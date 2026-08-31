package com.wally.customersupport.adapter.out.persistence.repository;

import java.util.List;
import java.util.UUID;

import com.wally.customersupport.adapter.out.persistence.entity.MessageJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataMessageRepository extends JpaRepository<MessageJpaEntity, UUID> {

    boolean existsByExternalMessageId(String externalMessageId);

    List<MessageJpaEntity> findTop20ByConversationIdOrderByOccurredAtDesc(UUID conversationId);
}
