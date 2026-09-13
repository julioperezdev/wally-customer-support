package com.wally.customersupport.order.infrastructure.repository.postgres;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.order.domain.model.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataOrderRepository extends JpaRepository<OrderJpaEntity, UUID> {

    Optional<OrderJpaEntity> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = "items")
    @Query("select distinct o from OrderJpaEntity o where o.id = :id")
    Optional<OrderJpaEntity> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = "items")
    @Query("select distinct o from OrderJpaEntity o where (:status is null or o.status = :status) order by o.createdAt desc")
    List<OrderJpaEntity> findRecentByStatus(@Param("status") OrderStatus status, Pageable pageable);
}
