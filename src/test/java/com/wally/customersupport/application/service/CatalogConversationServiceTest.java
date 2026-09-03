package com.wally.customersupport.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.domain.model.CatalogProduct;
import com.wally.customersupport.domain.model.CatalogVariant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogConversationServiceTest {

    @Mock
    private CatalogQueryService catalogQueryService;

    @Test
    void buildsReplyFromDeterministicCatalogData() {
        CatalogProduct product = product("Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", 12);
        when(catalogQueryService.search(argThat(query ->
                "remera".equals(query.name()) && "m".equals(query.size()) && "negro".equals(query.color()))))
                .thenReturn(List.of(product));

        java.util.Optional<String> reply = new CatalogConversationService(catalogQueryService)
                .replyFor("¿Tienen remera negra talle M?");

        assertTrue(reply.isPresent());
        assertTrue(reply.get().contains("Remera NullPointer"));
        assertTrue(reply.get().contains("18.900,00 ARS"));
        assertTrue(reply.get().contains("stock disponible: 12"));
        verify(catalogQueryService).search(argThat(query ->
                "remera".equals(query.name()) && "m".equals(query.size()) && "negro".equals(query.color())));
    }

    @Test
    void doesNotInventAProductWhenThereAreNoMatches() {
        when(catalogQueryService.search(argThat(query -> "remera fantasma".equals(query.name()))))
                .thenReturn(List.of());

        String reply = new CatalogConversationService(catalogQueryService)
                .replyFor("Busco Remera Fantasma")
                .orElseThrow();

        assertEquals(
                "No encontré coincidencias en el catálogo demo para esa consulta. "
                        + "No puedo confirmar disponibilidad fuera de los datos registrados.",
                reply);
    }

    @Test
    void asksForAFilterInsteadOfRunningAnUnboundedQuery() {
        String reply = new CatalogConversationService(catalogQueryService)
                .replyFor("¿Qué productos tienen?")
                .orElseThrow();

        assertEquals(
                "Para buscar en el catálogo, indicame el nombre del producto, SKU, talle o color.",
                reply);
    }

    private static CatalogProduct product(String name, String sku, String size, String color, int stock) {
        CatalogVariant variant = new CatalogVariant(
                UUID.randomUUID(), sku, size, color, new BigDecimal("18900.00"), "ARS", stock, true);
        return new CatalogProduct(UUID.randomUUID(), name, "demo", null, true, true, List.of(variant));
    }

}
