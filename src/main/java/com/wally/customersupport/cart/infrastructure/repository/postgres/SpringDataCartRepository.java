package com.wally.customersupport.cart.infrastructure.repository.postgres;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCartRepository extends JpaRepository<CartJpaEntity, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<CartJpaEntity> findByConversationId(UUID conversationId);
}
