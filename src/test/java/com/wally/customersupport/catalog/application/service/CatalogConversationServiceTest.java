package com.wally.customersupport.catalog.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

        java.util.Optional<String> reply = replyFor("¿Tienen remera negra talle M?");

        assertTrue(reply.isPresent());
        assertTrue(reply.get().contains("Remera NullPointer"));
        assertTrue(reply.get().contains("18.900,00 ARS"));
        assertTrue(reply.get().contains("stock disponible: 12"));
        verify(catalogQueryService).search(argThat(query ->
                query.name() == null && "m".equals(query.size()) && "negro".equals(query.color())
                && "remera".equals(query.productType())));
    }

    @Test
    void exposesImageForOneUnambiguousInitialMatch() {
        CatalogProduct product = product(
                "Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", 12,
                "wcs/catalog/00000000-0000-0000-0000-000000000001/remera.jpg");
        when(catalogQueryService.search(argThat(query -> true)))
                .thenReturn(List.of(product));

        CatalogSearchResult result = new CatalogConversationService(catalogQueryService)
                .search(new CatalogQuery("nullpointer", null, "M", "negro"), List.of(), null)
                .orElseThrow();

        assertEquals(
                "wcs/catalog/00000000-0000-0000-0000-000000000001/remera.jpg",
                result.singleImageReference().orElseThrow());
    }

    @Test
    void prefersTheVariantImageOverTheProductFallback() {
        CatalogProduct product = productWithVariantImage(
                "Remera NullPointer",
                "RP-REM-NP-BLA-M",
                "M",
                "Blanco",
                4,
                "wcs/catalog/product/default.png",
                "wcs/catalog/product/RP-REM-NP-BLA-M.png");
        when(catalogQueryService.search(any(CatalogQuery.class))).thenReturn(List.of(product));

        CatalogSearchResult result = new CatalogConversationService(catalogQueryService)
                .search(new CatalogQuery("nullpointer", null, "M", "blanco"), List.of(), null)
                .orElseThrow();

        assertEquals(
                "wcs/catalog/product/RP-REM-NP-BLA-M.png",
                result.singleImageReference().orElseThrow());
    }

    @Test
    void exactSearchDoesNotReconstructQuantityAsPartOfProductName() {
        CatalogProduct product = product("Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", 12);
        when(catalogQueryService.search(argThat(query ->
                "nullpointer".equals(query.name())
                        && "m".equalsIgnoreCase(query.size())
                        && "negro".equalsIgnoreCase(query.color())
                        && "remera".equalsIgnoreCase(query.productType()))))
                .thenReturn(List.of(product));

        CatalogSearchResult result = new CatalogConversationService(catalogQueryService)
                .searchExact(new CatalogQuery("nullpointer", null, "M", "negro", "remera"))
                .orElseThrow();

        assertEquals(CatalogSearchResult.Status.MATCHED, result.status());
        assertEquals("RP-REM-NP-NEG-M", result.facts().getFirst().sku());
        verify(catalogQueryService).search(argThat(query ->
                "nullpointer".equals(query.name()) && "remera".equalsIgnoreCase(query.productType())));
    }

    @Test
    void doesNotInventAProductWhenThereAreNoMatches() {
        when(catalogQueryService.search(argThat(query ->
                "fantasma".equals(query.name()) && "remera".equals(query.productType()))))
                .thenReturn(List.of());

        String reply = replyFor("Busco Remera Fantasma")
                .orElseThrow();

        assertEquals(
                "No encontré coincidencias en el catálogo demo para esa consulta. "
                        + "No puedo confirmar disponibilidad fuera de los datos registrados.",
                reply);
    }

    @Test
    void returnsAUsefulAlternativeWhenOneExplicitFilterHasNoMatch() {
        CatalogProduct blackXl = product("Buzo Spring Boot", "RP-BUZ-SB-NEG-XL", "XL", "Negro", 3);
        when(catalogQueryService.search(any(CatalogQuery.class)))
                .thenAnswer(invocation -> {
                    CatalogQuery query = invocation.getArgument(0);
                    return query != null && "buzo".equals(query.productType())
                            && "negro".equals(query.color()) && query.size() == null
                            ? List.of(blackXl)
                            : List.of();
                });

        String reply = replyFor("Busco un buzo negro talle L")
                .orElseThrow();

        assertTrue(reply.contains("No encontré buzo para esa consulta"));
        assertTrue(reply.contains("Buzo Spring Boot"));
        assertTrue(reply.contains("talle XL"));
    }

    @Test
    void prefersTheSameProductTypeBeforeOfferingAnotherCategory() {
        CatalogProduct blackXl = product("Buzo Spring Boot", "RP-BUZ-SB-NEG-XL", "XL", "Negro", 3);
        CatalogProduct blackLShirt = product("Remera NullPointer", "RP-REM-NP-NEG-L", "L", "Negro", 7);
        when(catalogQueryService.search(any(CatalogQuery.class)))
                .thenAnswer(invocation -> {
                    CatalogQuery query = invocation.getArgument(0);
                    if ("buzo".equals(query.productType()) && "negro".equals(query.color())
                            && query.size() == null) {
                        return List.of(blackXl);
                    }
                    if (query.productType() == null && "negro".equals(query.color())
                            && "l".equals(query.size())) {
                        return List.of(blackLShirt);
                    }
                    return List.of();
                });

        String reply = replyFor(
                        new CatalogQuery(null, null, "L", "negro", "buzo"),
                        List.of("Quiero un buzo", "Que sea negro"),
                        "¿Hay talle L?")
                .orElseThrow();

        assertTrue(reply.contains("Buzo Spring Boot"));
        assertTrue(reply.contains("talle XL"));
        assertTrue(!reply.contains("Remera NullPointer"));
    }

    @Test
    void explainsWhenTheCustomerUsesAnUnsupportedCatalogAttribute() {
        String reply = replyFor("Quiero una remera de manga larga")
                .orElseThrow();

        assertEquals(
                "Todavía no puedo filtrar por ese atributo. Puedo buscar por producto, talle, color, precio y stock.",
                reply);
    }

    @Test
    void returnsABoundedGeneralCatalogWhenNoFilterIsProvided() {
        when(catalogQueryService.searchAll(5))
                .thenReturn(List.of(
                        product("Buzo Spring Boot", "RP-BUZ-SB-GRI-L", "L", "Gris", 5),
                        product("Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", 12)));

        String reply = replyFor("¿Qué productos tienen?")
                .orElseThrow();

        assertTrue(reply.startsWith("Encontré estos productos:"));
        assertTrue(reply.contains("Buzo Spring Boot"));
        assertTrue(reply.contains("Remera NullPointer"));
    }

    @Test
    void ignoresStaleFiltersWhenTheCustomerRequestsTheWholeCatalog() {
        when(catalogQueryService.searchAll(5))
                .thenReturn(List.of(product("Buzo Spring Boot", "RP-BUZ-SB-GRI-L", "L", "Gris", 5)));

        CatalogSearchResult result = new CatalogConversationService(catalogQueryService)
                .search(
                        new CatalogQuery(null, null, "M", null, "buzo"),
                        List.of("Quiero la talla M", "Quiero un buzo"),
                        "¿Qué productos tienen?")
                .orElseThrow();

        verify(catalogQueryService).searchAll(5);
        assertEquals(CatalogSearchResult.Status.MATCHED, result.status());
    }

    @Test
    void answersAvailabilityUsingTheSinglePreviousCatalogResult() {
        CatalogProduct product = product("Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", 12);
        when(catalogQueryService.search(argThat(query ->
                "nullpointer".equals(query.name()) && "m".equalsIgnoreCase(query.size())
                        && "negro".equalsIgnoreCase(query.color()))))
                .thenReturn(List.of(product));

        String reply = replyFor(
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
    void answersHowManyRemainFromTheActiveVariant() {
        CatalogProduct product = product("Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", 12);
        when(catalogQueryService.search(argThat(query ->
                "remera".equals(query.productType()) && "m".equals(query.size())
                        && "negro".equals(query.color()))))
                .thenReturn(List.of(product));
        List<String> history = List.of("Busco una remera negra talle M");
        String latest = "¿Cuántas quedan?";
        CatalogQuery query = CatalogQueryParser.parseConversation(history, latest).orElseThrow();

        String reply = new CatalogConversationService(catalogQueryService)
                .search(query, history, latest)
                .map(CatalogResponseFormatter::render)
                .orElseThrow();

        assertEquals(
                "Sí, Remera NullPointer (SKU: RP-REM-NP-NEG-M) está disponible. Stock actual: 12 unidades.",
                reply);
    }

    @Test
    void findsAndIncludesTheImageForAVisualColorFollowUp() {
        CatalogProduct grayHoodie = product(
                "Buzo Spring Boot", "RP-BUZ-SB-GRI-L", "L", "Gris", 5,
                "wcs/catalog/buzo-spring-boot-gris.jpg");
        when(catalogQueryService.search(argThat(query ->
                "buzo".equals(query.productType()) && "gris".equals(query.color()))))
                .thenReturn(List.of(grayHoodie));
        List<String> history = List.of("Quiero un buzo");
        String latest = "¿Cómo se ve el gris?";
        CatalogQuery query = CatalogQueryParser.parseConversation(history, latest).orElseThrow();

        CatalogSearchResult result = new CatalogConversationService(catalogQueryService)
                .search(query, history, latest)
                .orElseThrow();

        assertEquals(CatalogSearchResult.Status.MATCHED, result.status());
        assertEquals("RP-BUZ-SB-GRI-L", result.facts().getFirst().sku());
        assertEquals("wcs/catalog/buzo-spring-boot-gris.jpg", result.singleImageReference().orElseThrow());
    }

    @Test
    void answersPriceUsingTheSinglePreviousCatalogResult() {
        CatalogProduct product = product("Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", 12);
        when(catalogQueryService.search(argThat(query ->
                "nullpointer".equals(query.name()) && "m".equalsIgnoreCase(query.size())
                        && "negro".equalsIgnoreCase(query.color()))))
                .thenReturn(List.of(product));

        String reply = replyFor(
                        new CatalogQuery("nullpointer", null, "M", "negro"),
                        List.of("Busco una remera NullPointer negra talle M"),
                        "¿Cuánto cuesta?")
                .orElseThrow();

        assertEquals(
                "El precio actual de Remera NullPointer (SKU: RP-REM-NP-NEG-M) es 18.900,00 ARS.",
                reply);
    }

    @Test
    void prefersExplicitCatalogFiltersOverAModelQuery() {
        CatalogProduct product = product("Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", 12);
        when(catalogQueryService.search(argThat(query ->
                query.name() == null
                        && "m".equals(query.size())
                        && "negro".equals(query.color())
                        && "remera".equals(query.productType())
                        && new BigDecimal("20000").equals(query.maxPrice()))))
                .thenReturn(List.of(product));

        CatalogSearchResult result = new CatalogConversationService(catalogQueryService)
                .search(
                        new CatalogQuery("frio", null, null, null),
                        List.of("Busco una remera negra talle M de menos de 20000 pesos"),
                        "Busco una remera negra talle M de menos de 20000 pesos")
                .orElseThrow();

        assertEquals(CatalogSearchResult.Status.MATCHED, result.status());
        verify(catalogQueryService).search(argThat(query ->
                query.name() == null
                        && "m".equals(query.size())
                        && "negro".equals(query.color())
                        && "remera".equals(query.productType())
                        && new BigDecimal("20000").equals(query.maxPrice())));
    }

    @Test
    void resolvesTheCheapestVariantFromTheActiveCatalogSelection() {
        when(catalogQueryService.search(argThat(query -> "buzo".equals(query.productType()))))
                .thenReturn(List.of(
                        productAtPrice("Buzo Spring Boot", "RP-BUZ-SB-GRI-L", "L", "Gris", 5, "42900.00"),
                        productAtPrice("Buzo Spring Boot", "RP-BUZ-SB-NEG-XL", "XL", "Negro", 3, "39900.00")));

        CatalogSearchResult result = new CatalogConversationService(catalogQueryService)
                .search(
                        CatalogQuery.empty(),
                        List.of("Quiero un buzo"),
                        "Algo como lo de antes pero más barato")
                .orElseThrow();

        assertEquals(CatalogSearchResult.Status.MATCHED, result.status());
        assertEquals(List.of("RP-BUZ-SB-NEG-XL"), result.facts().stream().map(CatalogFact::sku).toList());
        assertEquals("CHEAPEST_MATCH", result.reason());
    }

    @Test
    void keepsExplicitCheaperThanPriceAsAFilterInsteadOfSelectingOnlyTheCheapest() {
        CatalogProduct cheaper = productAtPrice(
                "Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", 12, "18900.00");
        CatalogProduct secondMatch = productAtPrice(
                "Remera NullPointer", "RP-REM-NP-NEG-L", "L", "Negro", 7, "19900.00");
        when(catalogQueryService.search(argThat(query ->
                query != null
                        && "remera".equals(query.productType())
                        && new BigDecimal("20000").equals(query.maxPrice()))))
                .thenReturn(List.of(cheaper, secondMatch));

        CatalogSearchResult result = new CatalogConversationService(catalogQueryService)
                .search(
                        CatalogQuery.empty(),
                        List.of(),
                        "Busco una remera más barata que 20.000 pesos")
                .orElseThrow();

        assertEquals(CatalogSearchResult.Status.MATCHED, result.status());
        assertEquals(2, result.resultCount());
        assertEquals("MATCHED", result.reason());
    }

    @Test
    void searchesWarmClothingAcrossSweatersAndJackets() {
        when(catalogQueryService.search(argThat(query -> query != null && "buzo".equals(query.productType()))))
                .thenReturn(List.of(product("Buzo Spring Boot", "RP-BUZ-SB-NEG-XL", "XL", "Negro", 3)));
        when(catalogQueryService.search(argThat(query -> query != null && "campera".equals(query.productType()))))
                .thenReturn(List.of(product("Campera Deploy Friday", "RP-CAM-DF-AZU-M", "M", "Azul", 4)));

        CatalogSearchResult result = new CatalogConversationService(catalogQueryService)
                .search(
                        CatalogQuery.empty(),
                        List.of(),
                        "Busco algo para el frío")
                .orElseThrow();

        assertEquals(CatalogSearchResult.Status.MATCHED, result.status());
        assertEquals(2, result.resultCount());
    }

    @Test
    void ignoresPreferenceWordsWhenFilteringWarmClothingBySize() {
        when(catalogQueryService.search(argThat(query ->
                query != null && "buzo".equals(query.productType()) && "l".equals(query.size()))))
                .thenReturn(List.of(product("Buzo Spring Boot", "RP-BUZ-SB-GRI-L", "L", "Gris", 5)));
        when(catalogQueryService.search(argThat(query ->
                query != null && "campera".equals(query.productType()) && "l".equals(query.size()))))
                .thenReturn(List.of());

        String reply = replyFor("Quiero algo para el frío, preferentemente talle L")
                .orElseThrow();

        assertTrue(reply.contains("Buzo Spring Boot"));
        assertTrue(!reply.contains("preferentemente"));
    }

    @Test
    void asksToDisambiguateAvailabilityWhenSeveralVariantsMatch() {
        when(catalogQueryService.search(argThat(query -> "remera".equals(query.productType()))))
                .thenReturn(List.of(
                        product("Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", 12),
                        product("Remera NullPointer", "RP-REM-NP-NEG-L", "L", "Negro", 7)));

        String reply = replyFor(
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
        String reply = replyFor(new com.wally.customersupport.catalog.domain.model.CatalogQuery(
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

        String reply = replyFor(
                        new CatalogQuery(null, null, null, null),
                        List.of("Mejor un buzo"),
                        "¿Qué opciones tienen?")
                .orElseThrow();

        assertTrue(reply.contains("Buzo Spring Boot"));
        verify(catalogQueryService).search(argThat(query -> "buzo".equals(query.productType())));
    }

    @Test
    void resolvesAFilterOnlySizeRefinementAgainstTheActiveCatalogSelection() {
        CatalogProduct product = product("Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", 12);
        when(catalogQueryService.search(argThat(query ->
                "m".equals(query.size())
                        && "negro".equals(query.color())
                        && "remera".equals(query.productType()))))
                .thenReturn(List.of(product));

        CatalogSearchResult result = new CatalogConversationService(catalogQueryService)
                .search(
                        new CatalogQuery(null, null, "M", null),
                        List.of("Quiero la talle M", "Busco una remera negra"),
                        "Quiero la talle M")
                .orElseThrow();

        assertEquals(CatalogSearchResult.Status.MATCHED, result.status());
        assertEquals("RP-REM-NP-NEG-M", result.facts().getFirst().sku());
        verify(catalogQueryService).search(argThat(query ->
                "m".equals(query.size())
                        && "negro".equals(query.color())
                        && "remera".equals(query.productType())));
    }

    @Test
    void explainsWhenTheRequestedCatalogCategoryIsNotSupported() {
        String reply = replyFor("¿Venden gorras?")
                .orElseThrow();

        assertEquals(
                "Por ahora no ofrecemos gorras. Nuestro catálogo actual incluye remeras, buzos y camperas. "
                        + "Si querés, puedo mostrarte esas opciones.",
                reply);
    }

    private java.util.Optional<String> replyFor(String message) {
        CatalogQuery query = CatalogQueryParser.parse(message).orElse(CatalogQuery.empty());
        return new CatalogConversationService(catalogQueryService)
                .search(query, List.of(), message)
                .map(CatalogResponseFormatter::render);
    }

    private java.util.Optional<String> replyFor(
            CatalogQuery query,
            List<String> recentMessages,
            String latestMessage) {
        return new CatalogConversationService(catalogQueryService)
                .search(query, recentMessages, latestMessage)
                .map(CatalogResponseFormatter::render);
    }

    private static CatalogProduct product(String name, String sku, String size, String color, int stock) {
        return product(name, sku, size, color, stock, null);
    }

    private static CatalogProduct product(
            String name,
            String sku,
            String size,
            String color,
            int stock,
            String imageObjectKey) {
        return productAtPrice(name, sku, size, color, stock, "18900.00", imageObjectKey);
    }

    private static CatalogProduct productAtPrice(
            String name,
            String sku,
            String size,
            String color,
            int stock,
            String price) {
        return productAtPrice(name, sku, size, color, stock, price, null);
    }

    private static CatalogProduct productAtPrice(
            String name,
            String sku,
            String size,
            String color,
            int stock,
            String price,
            String imageObjectKey) {
        CatalogVariant variant = new CatalogVariant(
                UUID.randomUUID(), sku, size, color, new BigDecimal(price), "ARS", stock, true);
        return new CatalogProduct(UUID.randomUUID(), name, "demo", imageObjectKey, true, true, List.of(variant));
    }

    private static CatalogProduct productWithVariantImage(
            String name,
            String sku,
            String size,
            String color,
            int stock,
            String productImageObjectKey,
            String variantImageObjectKey) {
        CatalogVariant variant = new CatalogVariant(
                UUID.randomUUID(),
                sku,
                size,
                color,
                new BigDecimal("18900.00"),
                "ARS",
                stock,
                true,
                variantImageObjectKey);
        return new CatalogProduct(
                UUID.randomUUID(), name, "demo", productImageObjectKey, true, true, List.of(variant));
    }

}
