package com.wally.customersupport.catalog.infrastructure.repository.postgres;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface SpringDataCatalogVariantRepository extends JpaRepository<CatalogVariantJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CatalogVariantJpaEntity> findBySku(String sku);
}
