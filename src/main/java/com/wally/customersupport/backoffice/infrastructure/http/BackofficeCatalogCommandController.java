package com.wally.customersupport.backoffice.infrastructure.http;

import java.util.Map;

import com.wally.customersupport.backoffice.application.model.BackofficeStockAdjustment;
import com.wally.customersupport.backoffice.application.service.BackofficeAccessService;
import com.wally.customersupport.backoffice.application.service.BackofficeStockAdjustmentService;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/backoffice/catalog")
@RequiredArgsConstructor
@Slf4j
public class BackofficeCatalogCommandController {

    private final BackofficeAccessService accessService;
    private final BackofficeStockAdjustmentService stockAdjustmentService;

    @PostMapping("/variants/{sku}/stock")
    public ResponseEntity<?> adjustStock(
            @PathVariable String sku,
            @RequestBody StockAdjustmentRequest request,
            @RequestHeader("X-WCS-Actor-Key") String actorKey,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        BackofficeAccessService.Decision decision = accessService.authorize("backoffice.catalog.write");
        if (!decision.authorized()) {
            StructuredEventLog.warn(log, "BACKOFFICE_ACCESS_DENIED", Map.of(
                    "operation", "catalog.stock.adjust",
                    "reason", decision.reason()));
            return ResponseEntity.status(decision.status()).body(Map.of("code", decision.reason()));
        }
        try {
            BackofficeStockAdjustment result = stockAdjustmentService.adjust(
                    sku, request.delta(), request.reason(), actorKey, idempotencyKey);
            StructuredEventLog.info(log, "BACKOFFICE_CATALOG_STOCK_ADJUSTED", Map.of(
                    "operation", "catalog.stock.adjust",
                    "sku", sku,
                    "delta", result.delta(),
                    "newStock", result.newStock()));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", "INVALID_STOCK_ADJUSTMENT"));
        } catch (ArithmeticException exception) {
            return ResponseEntity.badRequest().body(Map.of("code", "STOCK_OVERFLOW"));
        }
    }

    public record StockAdjustmentRequest(int delta, String reason) {
    }
}
