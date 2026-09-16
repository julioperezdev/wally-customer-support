package com.wally.customersupport.cart.infrastructure.repository.postgres;

import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.cart.application.port.out.CartRepository;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCartRepositoryAdapter implements CartRepository {

    private final SpringDataCartRepository repository;

    public JpaCartRepositoryAdapter(SpringDataCartRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CartJpaEntity> findByConversationId(UUID conversationId) {
        return repository.findByConversationId(conversationId);
    }

    @Override
    public CartJpaEntity saveAndFlush(CartJpaEntity cart) {
        return repository.saveAndFlush(cart);
    }
}
