package com.wally.customersupport.catalog.infrastructure.repository.postgres;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.wally.customersupport.catalog.infrastructure.repository.postgres.CatalogProductJpaEntity;
import com.wally.customersupport.catalog.infrastructure.repository.postgres.SpringDataCatalogProductRepository;
import com.wally.customersupport.catalog.domain.model.CatalogProduct;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.catalog.domain.model.CatalogVariant;
import org.junit.jupiter.api.Test;

class JpaCatalogRepositoryAdapterTest {

    @Test
    void bindsAbsentFiltersAsTextInsteadOfNull() {
        SpringDataCatalogProductRepository repository = mock(SpringDataCatalogProductRepository.class);
        CatalogProductJpaEntity entity = mock(CatalogProductJpaEntity.class);
        CatalogProduct product = mock(CatalogProduct.class);
        CatalogQuery query = new CatalogQuery("Remera", null, "M", "Negro");

        when(repository.search("remera", "", "m", "negro")).thenReturn(List.of(entity));
        when(entity.toDomain(query)).thenReturn(product);
        when(product.variants()).thenReturn(List.of(mock(CatalogVariant.class)));

        new JpaCatalogRepositoryAdapter(repository).search(query);

        verify(repository).search(eq("remera"), eq(""), eq("m"), eq("negro"));
    }
}
