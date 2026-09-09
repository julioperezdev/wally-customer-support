package com.wally.customersupport.catalog.infrastructure.repository.postgres;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataCatalogStockAdjustmentRepository
        extends JpaRepository<CatalogStockAdjustmentJpaEntity, java.util.UUID> {

    Optional<CatalogStockAdjustmentJpaEntity> findByIdempotencyKey(String idempotencyKey);
}
