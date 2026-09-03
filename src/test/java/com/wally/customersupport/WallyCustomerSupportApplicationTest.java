package com.wally.customersupport;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;
import com.wally.customersupport.application.service.CatalogQueryService;
import com.wally.customersupport.application.service.SupportConfigurationQueryService;
import com.wally.customersupport.domain.model.CatalogQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WallyCustomerSupportApplicationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CatalogQueryService catalogQueryService;

    @Autowired
    private SupportConfigurationQueryService supportConfigurationQueryService;

    @Test
    void startsWithFlywayAndTestAdapters() {
        assertNotNull(dataSource);
    }

    @Test
    void loadsDemoCatalogWithDeterministicVariantData() {
        var products = catalogQueryService.search(new CatalogQuery("NullPointer", null, "M", "Negro"));

        assertEquals(1, products.size());
        assertEquals("Remera NullPointer", products.getFirst().name());
        assertEquals("RP-REM-NP-NEG-M", products.getFirst().variants().getFirst().sku());
        assertEquals("ARS", products.getFirst().variants().getFirst().currency());
        assertEquals(12, products.getFirst().variants().getFirst().stock());
        assertTrue(products.getFirst().demo());
    }

    @Test
    void loadsDemoBusinessHoursAndVersionedPolicy() {
        var hours = supportConfigurationQueryService.businessHours();
        var policy = supportConfigurationQueryService.activePolicy("returns");

        assertEquals(7, hours.size());
        assertTrue(hours.getLast().closed());
        assertTrue(policy.isPresent());
        assertEquals(1, policy.get().version());
        assertTrue(policy.get().demo());
    }
}
