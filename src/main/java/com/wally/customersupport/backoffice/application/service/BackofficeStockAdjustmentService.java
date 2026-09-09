package com.wally.customersupport.backoffice.application.service;

import java.time.Clock;
import java.time.Instant;

import com.wally.customersupport.backoffice.application.model.BackofficeStockAdjustment;
import com.wally.customersupport.catalog.infrastructure.repository.postgres.CatalogStockAdjustmentJpaEntity;
import com.wally.customersupport.catalog.infrastructure.repository.postgres.CatalogVariantJpaEntity;
import com.wally.customersupport.catalog.infrastructure.repository.postgres.SpringDataCatalogStockAdjustmentRepository;
import com.wally.customersupport.catalog.infrastructure.repository.postgres.SpringDataCatalogVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BackofficeStockAdjustmentService {

    private final SpringDataCatalogVariantRepository variantRepository;
    private final SpringDataCatalogStockAdjustmentRepository adjustmentRepository;
    private final Clock clock;

    @Transactional
    public BackofficeStockAdjustment adjust(String sku, int delta, String reason, String actorKey, String idempotencyKey) {
        requireText(sku, "sku", 80);
        requireText(reason, "reason", 256);
        requireText(actorKey, "actorKey", 128);
        requireText(idempotencyKey, "idempotencyKey", 128);
        if (delta == 0) {
            throw new IllegalArgumentException("delta must not be zero");
        }
        var previous = adjustmentRepository.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            return toView(previous.get());
        }
        CatalogVariantJpaEntity variant = variantRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("variant not found"));
        int previousStock = variant.getStock();
        int newStock = Math.addExact(previousStock, delta);
        if (newStock < 0) {
            throw new IllegalArgumentException("stock cannot be negative");
        }
        Instant now = Instant.now(clock);
        variant.adjustStock(newStock, now);
        CatalogStockAdjustmentJpaEntity saved = adjustmentRepository.save(
                new CatalogStockAdjustmentJpaEntity(sku, previousStock, delta, newStock, reason, actorKey, idempotencyKey, now));
        return toView(saved);
    }

    private static BackofficeStockAdjustment toView(CatalogStockAdjustmentJpaEntity entity) {
        var view = entity.toView();
        return new BackofficeStockAdjustment(
                view.id(), view.sku(), view.previousStock(), view.delta(), view.newStock(), view.reason(),
                view.actorKey(), view.idempotencyKey(), view.createdAt());
    }

    private static void requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }
}
