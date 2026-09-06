package com.wally.customersupport.conversation.infrastructure.repository.postgres;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataConversationMemoryRepository
        extends JpaRepository<ConversationMemoryJpaEntity, UUID> {
}
