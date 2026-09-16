package com.wally.customersupport.cart.infrastructure.repository.postgres;

import java.util.Optional;

import com.wally.customersupport.cart.application.port.out.CartCatalogReader;
import com.wally.customersupport.catalog.infrastructure.repository.postgres.CatalogVariantJpaEntity;
import com.wally.customersupport.catalog.infrastructure.repository.postgres.SpringDataCatalogVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JpaCartCatalogReader implements CartCatalogReader {

    private final SpringDataCatalogVariantRepository repository;

    @Override
    public Optional<CatalogItem> findBySku(String sku) {
        return repository.findBySku(sku).map(JpaCartCatalogReader::toItem);
    }

    private static CatalogItem toItem(CatalogVariantJpaEntity variant) {
        return new CatalogItem(
                variant.getSku(),
                variant.getProductName(),
                variant.getSizeLabel(),
                variant.getColor(),
                variant.getPrice(),
                variant.getCurrency(),
                variant.getStock(),
                variant.isActive());
    }
}
