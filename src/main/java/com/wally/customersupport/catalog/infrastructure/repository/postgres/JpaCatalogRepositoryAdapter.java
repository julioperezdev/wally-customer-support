package com.wally.customersupport.catalog.infrastructure.repository.postgres;

import java.util.List;
import java.util.Locale;

import com.wally.customersupport.catalog.application.port.out.CatalogRepository;
import com.wally.customersupport.catalog.domain.model.CatalogProduct;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCatalogRepositoryAdapter implements CatalogRepository {

    private final SpringDataCatalogProductRepository repository;

    public JpaCatalogRepositoryAdapter(SpringDataCatalogProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<CatalogProduct> search(CatalogQuery query) {
        return map(repository.search(
                filterValue(query.name()),
                filterValue(query.sku()),
                filterValue(query.size()),
                filterValue(query.color()),
                filterValue(query.productType()),
                query.minPrice(),
                query.maxPrice(),
                Pageable.unpaged()), query);
    }

    @Override
    public List<CatalogProduct> search(CatalogQuery query, int maxResults) {
        if (maxResults <= 0) {
            return List.of();
        }
        return map(repository.search(
                filterValue(query.name()),
                filterValue(query.sku()),
                filterValue(query.size()),
                filterValue(query.color()),
                filterValue(query.productType()),
                query.minPrice(),
                query.maxPrice(),
                PageRequest.of(0, maxResults)), query);
    }

    private static List<CatalogProduct> map(
            List<CatalogProductJpaEntity> products,
            CatalogQuery query) {
        return products.stream()
                .map(product -> product.toDomain(query))
                .filter(product -> !product.variants().isEmpty())
                .toList();
    }

    private static String filterValue(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
