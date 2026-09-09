package com.wally.customersupport.backoffice.infrastructure.http;

import java.math.BigDecimal;
import java.util.Map;

import com.wally.customersupport.backoffice.application.model.BackofficeCatalogPage;
import com.wally.customersupport.backoffice.application.service.BackofficeAccessService;
import com.wally.customersupport.backoffice.application.service.BackofficeCatalogQueryService;
import com.wally.customersupport.shared.infrastructure.observability.StructuredEventLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/backoffice/catalog")
@RequiredArgsConstructor
@Slf4j
public class BackofficeCatalogController {

    private final BackofficeAccessService accessService;
    private final BackofficeCatalogQueryService queryService;

    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String sku,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit) {
        BackofficeAccessService.Decision decision = accessService.authorize("backoffice.catalog.read");
        if (!decision.authorized()) {
            StructuredEventLog.warn(log, "BACKOFFICE_ACCESS_DENIED", Map.of(
                    "operation", "catalog.search",
                    "reason", decision.reason()));
            return ResponseEntity.status(decision.status()).body(Map.of("code", decision.reason()));
        }
        BackofficeCatalogPage result = queryService.search(new BackofficeCatalogQueryService.Filters(
                name, sku, size, color, productType, minPrice, maxPrice, page, limit));
        StructuredEventLog.info(log, "BACKOFFICE_CATALOG_VIEWED", Map.of(
                "operation", "catalog.search",
                "resultCount", result.items().size(),
                "page", result.page()));
        return ResponseEntity.ok(result);
    }
}
