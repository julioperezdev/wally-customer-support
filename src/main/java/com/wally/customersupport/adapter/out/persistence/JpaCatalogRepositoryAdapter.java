package com.wally.customersupport.adapter.out.persistence;

import java.util.List;

import com.wally.customersupport.adapter.out.persistence.repository.SpringDataCatalogProductRepository;
import com.wally.customersupport.application.port.out.CatalogRepository;
import com.wally.customersupport.domain.model.CatalogProduct;
import com.wally.customersupport.domain.model.CatalogQuery;
import org.springframework.stereotype.Repository;

@Repository
public class JpaCatalogRepositoryAdapter implements CatalogRepository {

    private final SpringDataCatalogProductRepository repository;

    public JpaCatalogRepositoryAdapter(SpringDataCatalogProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<CatalogProduct> search(CatalogQuery query) {
        return repository.search(query.name(), query.sku(), query.size(), query.color()).stream()
                .map(product -> product.toDomain(query))
                .filter(product -> !product.variants().isEmpty())
                .toList();
    }
}
