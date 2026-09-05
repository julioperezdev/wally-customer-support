package com.wally.customersupport.catalog.infrastructure.repository.postgres;

import java.util.List;
import java.util.Locale;

import com.wally.customersupport.catalog.infrastructure.repository.postgres.SpringDataCatalogProductRepository;
import com.wally.customersupport.catalog.application.port.out.CatalogRepository;
import com.wally.customersupport.catalog.domain.model.CatalogProduct;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCatalogRepositoryAdapter implements CatalogRepository {

    private final SpringDataCatalogProductRepository repository;

    public JpaCatalogRepositoryAdapter(SpringDataCatalogProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<CatalogProduct> search(CatalogQuery query) {
        // PostgreSQL cannot infer the type of a null parameter used in an optional filter.
        return repository.search(filterValue(query.name()), filterValue(query.sku()),
                        filterValue(query.size()), filterValue(query.color())).stream()
                .map(product -> product.toDomain(query))
                .filter(product -> !product.variants().isEmpty())
                .toList();
    }

    private static String filterValue(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
