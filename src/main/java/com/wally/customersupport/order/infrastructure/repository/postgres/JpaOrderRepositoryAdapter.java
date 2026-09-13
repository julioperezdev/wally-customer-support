package com.wally.customersupport.order.infrastructure.repository.postgres;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.order.application.port.out.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
public class JpaOrderRepositoryAdapter implements OrderRepository {

    private final SpringDataOrderRepository repository;

    public JpaOrderRepositoryAdapter(SpringDataOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<OrderJpaEntity> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<OrderJpaEntity> findByIdWithItems(UUID id) {
        return repository.findDetailedById(id);
    }

    @Override
    public Optional<OrderJpaEntity> findByIdempotencyKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public List<OrderJpaEntity> findByStatus(String status, int limit) {
        return repository.findRecentByStatus(
                status == null || status.isBlank() ? null : com.wally.customersupport.order.domain.model.OrderStatus.valueOf(status),
                PageRequest.of(0, Math.max(1, Math.min(100, limit))));
    }

    @Override
    public OrderJpaEntity saveAndFlush(OrderJpaEntity order) {
        return repository.saveAndFlush(order);
    }
}
