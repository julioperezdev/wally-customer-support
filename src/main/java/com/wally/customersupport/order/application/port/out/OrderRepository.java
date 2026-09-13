package com.wally.customersupport.order.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.order.infrastructure.repository.postgres.OrderJpaEntity;

public interface OrderRepository {

    Optional<OrderJpaEntity> findById(UUID id);

    Optional<OrderJpaEntity> findByIdWithItems(UUID id);

    Optional<OrderJpaEntity> findByIdempotencyKey(String idempotencyKey);

    List<OrderJpaEntity> findByStatus(String status, int limit);

    OrderJpaEntity saveAndFlush(OrderJpaEntity order);
}
