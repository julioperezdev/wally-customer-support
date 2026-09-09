package com.wally.customersupport.backoffice.application.service;

import java.math.BigDecimal;
import java.util.List;

import com.wally.customersupport.backoffice.application.model.BackofficeCatalogPage;
import com.wally.customersupport.backoffice.application.model.BackofficeCatalogProduct;
import com.wally.customersupport.catalog.application.service.CatalogQueryService;
import com.wally.customersupport.catalog.domain.model.CatalogProduct;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BackofficeCatalogQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SCAN_SIZE = 500;

    private final CatalogQueryService catalogQueryService;

    @Transactional(readOnly = true)
    public BackofficeCatalogPage search(Filters filters) {
        int page = Math.max(0, filters.page());
        int size = Math.min(MAX_PAGE_SIZE, Math.max(1, filters.size()));
        int scanSize = Math.min(MAX_SCAN_SIZE, (page + 1) * size + 1);
        CatalogQuery query = new CatalogQuery(
                filters.name(),
                filters.sku(),
                filters.sizeLabel(),
                filters.color(),
                filters.productType(),
                filters.minPrice(),
                filters.maxPrice());
        List<CatalogProduct> products = catalogQueryService.search(query, scanSize);
        int from = Math.min(page * size, products.size());
        int to = Math.min(from + size, products.size());
        boolean hasNext = products.size() > to;
        return new BackofficeCatalogPage(
                products.subList(from, to).stream().map(BackofficeCatalogQueryService::toView).toList(),
                page,
                size,
                hasNext);
    }

    private static BackofficeCatalogProduct toView(CatalogProduct product) {
        return new BackofficeCatalogProduct(
                product.id(),
                product.name(),
                product.description(),
                product.productType(),
                product.imageObjectKey(),
                product.active(),
                product.demo(),
                product.variants().stream()
                        .map(variant -> new BackofficeCatalogProduct.BackofficeCatalogVariant(
                                variant.id(),
                                variant.sku(),
                                variant.size(),
                                variant.color(),
                                variant.price(),
                                variant.currency(),
                                variant.stock(),
                                variant.active()))
                        .toList());
    }

    public record Filters(
            String name,
            String sku,
            String sizeLabel,
            String color,
            String productType,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            int page,
            int size) {
    }
}
