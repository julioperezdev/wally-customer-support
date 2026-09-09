package com.wally.customersupport.catalog.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.wally.customersupport.catalog.domain.model.CatalogProduct;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.catalog.domain.model.CatalogVariant;
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
                query.name() == null && "m".equals(query.size()) && "negro".equals(query.color())
                        && "remera".equals(query.productType()))))
                .thenReturn(List.of(product));

        java.util.Optional<String> reply = new CatalogConversationService(catalogQueryService)
                .replyFor("¿Tienen remera negra talle M?");

        assertTrue(reply.isPresent());
        assertTrue(reply.get().contains("Remera NullPointer"));
        assertTrue(reply.get().contains("18.900,00 ARS"));
        assertTrue(reply.get().contains("stock disponible: 12"));
        verify(catalogQueryService).search(argThat(query ->
                query.name() == null && "m".equals(query.size()) && "negro".equals(query.color())
                        && "remera".equals(query.productType())));
    }

    @Test
    void doesNotInventAProductWhenThereAreNoMatches() {
        when(catalogQueryService.search(argThat(query ->
                "fantasma".equals(query.name()) && "remera".equals(query.productType()))))
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
    void returnsABoundedGeneralCatalogWhenNoFilterIsProvided() {
        when(catalogQueryService.searchAll(5))
                .thenReturn(List.of(
                        product("Buzo Spring Boot", "RP-BUZ-SB-GRI-L", "L", "Gris", 5),
                        product("Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", 12)));

        String reply = new CatalogConversationService(catalogQueryService)
                .replyFor("¿Qué productos tienen?")
                .orElseThrow();

        assertTrue(reply.startsWith("Encontré estos productos:"));
        assertTrue(reply.contains("Buzo Spring Boot"));
        assertTrue(reply.contains("Remera NullPointer"));
    }

    @Test
    void answersAvailabilityUsingTheSinglePreviousCatalogResult() {
        CatalogProduct product = product("Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", 12);
        when(catalogQueryService.search(argThat(query ->
                "nullpointer".equals(query.name()) && "m".equalsIgnoreCase(query.size())
                        && "negro".equalsIgnoreCase(query.color()))))
                .thenReturn(List.of(product));

        String reply = new CatalogConversationService(catalogQueryService)
                .replyFor(
                        new com.wally.customersupport.catalog.domain.model.CatalogQuery(
                                "nullpointer", null, "M", "negro"),
                        List.of("Busco una remera NullPointer negra talle M"),
                        "¿Está disponible?")
                .orElseThrow();

        assertEquals(
                "Sí, Remera NullPointer (SKU: RP-REM-NP-NEG-M) está disponible. Stock actual: 12 unidades.",
                reply);
    }

    @Test
    void answersPriceUsingTheSinglePreviousCatalogResult() {
        CatalogProduct product = product("Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", 12);
        when(catalogQueryService.search(argThat(query ->
                "nullpointer".equals(query.name()) && "m".equalsIgnoreCase(query.size())
                        && "negro".equalsIgnoreCase(query.color()))))
                .thenReturn(List.of(product));

        String reply = new CatalogConversationService(catalogQueryService)
                .replyFor(
                        new CatalogQuery("nullpointer", null, "M", "negro"),
                        List.of("Busco una remera NullPointer negra talle M"),
                        "¿Cuánto cuesta?")
                .orElseThrow();

        assertEquals(
                "El precio actual de Remera NullPointer (SKU: RP-REM-NP-NEG-M) es 18.900,00 ARS.",
                reply);
    }

    @Test
    void asksToDisambiguateAvailabilityWhenSeveralVariantsMatch() {
        when(catalogQueryService.search(argThat(query -> "remera".equals(query.productType()))))
                .thenReturn(List.of(
                        product("Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", 12),
                        product("Remera NullPointer", "RP-REM-NP-NEG-L", "L", "Negro", 7)));

        String reply = new CatalogConversationService(catalogQueryService)
                .replyFor(
                        new com.wally.customersupport.catalog.domain.model.CatalogQuery(
                                null, null, null, null, "remera"),
                        List.of("¿Qué remeras tienen?"),
                        "¿Está disponible?")
                .orElseThrow();

        assertEquals(
                "Encontré varias opciones. Indicame el SKU o el producto exacto que querés consultar.",
                reply);
    }

    @Test
    void asksForProductWhenAvailabilityHasNoConversationContext() {
        String reply = new CatalogConversationService(catalogQueryService)
                .replyFor(new com.wally.customersupport.catalog.domain.model.CatalogQuery(
                                null, null, null, null),
                        List.of(),
                        "¿Está disponible?")
                .orElseThrow();

        assertEquals("¿De qué producto o SKU querés conocer ese dato?", reply);
    }

    @Test
    void preservesTheActiveTypeWhenTheCustomerAsksForMoreOptions() {
        when(catalogQueryService.search(argThat(query ->
                "buzo".equals(query.productType()) && query.name() == null)))
                .thenReturn(List.of(product("Buzo Spring Boot", "RP-BUZ-SB-GRI-L", "L", "Gris", 5)));

        String reply = new CatalogConversationService(catalogQueryService)
                .replyFor(
                        new CatalogQuery(null, null, null, null),
                        List.of("Mejor un buzo"),
                        "¿Qué opciones tienen?")
                .orElseThrow();

        assertTrue(reply.contains("Buzo Spring Boot"));
        verify(catalogQueryService).search(argThat(query -> "buzo".equals(query.productType())));
    }

    @Test
    void usesTheSameSafeFallbackForAnUnsupportedCatalogCategory() {
        when(catalogQueryService.search(argThat(query -> "gorras".equals(query.name()))))
                .thenReturn(List.of());

        String reply = new CatalogConversationService(catalogQueryService)
                .replyFor("¿Venden gorras?")
                .orElseThrow();

        assertEquals(
                "No encontré coincidencias en el catálogo demo para esa consulta. "
                        + "No puedo confirmar disponibilidad fuera de los datos registrados.",
                reply);
    }

    private static CatalogProduct product(String name, String sku, String size, String color, int stock) {
        CatalogVariant variant = new CatalogVariant(
                UUID.randomUUID(), sku, size, color, new BigDecimal("18900.00"), "ARS", stock, true);
        return new CatalogProduct(UUID.randomUUID(), name, "demo", null, true, true, List.of(variant));
    }

}
