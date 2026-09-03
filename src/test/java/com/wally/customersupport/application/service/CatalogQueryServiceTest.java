package com.wally.customersupport.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wally.customersupport.application.port.out.CatalogRepository;
import com.wally.customersupport.domain.model.CatalogQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogQueryServiceTest {

    @Mock
    private CatalogRepository catalogRepository;

    @Test
    void doesNotRunAnUnboundedCatalogQuery() {
        CatalogQueryService service = new CatalogQueryService(catalogRepository);

        assertTrue(service.search(new CatalogQuery(" ", null, null, null)).isEmpty());
        verify(catalogRepository, never()).search(new CatalogQuery(null, null, null, null));
    }

    @Test
    void normalizesFiltersBeforeTheyReachThePort() {
        CatalogQuery query = new CatalogQuery("  Buzo  ", " RP-1 ", " L ", " Gris ");

        assertEquals("Buzo", query.name());
        assertEquals("RP-1", query.sku());
        assertEquals("L", query.size());
        assertEquals("Gris", query.color());
    }
}
