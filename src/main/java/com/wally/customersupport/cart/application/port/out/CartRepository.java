package com.wally.customersupport.cart.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.cart.infrastructure.repository.postgres.CartJpaEntity;

public interface CartRepository {

    Optional<CartJpaEntity> findByConversationId(UUID conversationId);

    CartJpaEntity saveAndFlush(CartJpaEntity cart);
}
