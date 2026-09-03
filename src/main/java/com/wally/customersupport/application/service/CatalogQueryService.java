package com.wally.customersupport.application.service;

import java.util.List;

import com.wally.customersupport.application.port.out.CatalogRepository;
import com.wally.customersupport.domain.model.CatalogProduct;
import com.wally.customersupport.domain.model.CatalogQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogQueryService {

    private final CatalogRepository catalogRepository;

    public CatalogQueryService(CatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    @Transactional(readOnly = true)
    public List<CatalogProduct> search(CatalogQuery query) {
        if (query == null || query.isEmpty()) {
            return List.of();
        }
        return List.copyOf(catalogRepository.search(query));
    }
}
