package com.wally.customersupport.catalog.application.service;

import java.util.List;

import com.wally.customersupport.catalog.application.port.out.CatalogRepository;
import com.wally.customersupport.catalog.domain.model.CatalogProduct;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogQueryService {

    private final CatalogRepository catalogRepository;

    @Transactional(readOnly = true)
    public List<CatalogProduct> search(CatalogQuery query) {
        if (query == null || query.isEmpty()) {
            return List.of();
        }
        return List.copyOf(catalogRepository.search(query));
    }

    /**
     * Returns a bounded catalog listing for an intentionally unfiltered query.
     * Filtered searches remain separate so an accidental empty query cannot
     * silently become an unbounded use-case.
     */
    @Transactional(readOnly = true)
    public List<CatalogProduct> searchAll(int maxResults) {
        if (maxResults <= 0) {
            return List.of();
        }
        return List.copyOf(catalogRepository.search(CatalogQuery.empty(), maxResults));
    }
}
