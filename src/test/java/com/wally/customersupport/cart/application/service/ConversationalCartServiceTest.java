package com.wally.customersupport.cart.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.catalog.application.service.CatalogConversationService;
import com.wally.customersupport.catalog.application.service.CatalogFact;
import com.wally.customersupport.catalog.application.service.CatalogSearchResult;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.cart.application.port.out.CartCatalogReader;
import com.wally.customersupport.cart.application.port.out.CartCheckoutCreator;
import com.wally.customersupport.cart.application.port.out.CartRepository;
import com.wally.customersupport.cart.domain.model.CartStatus;
import com.wally.customersupport.cart.infrastructure.repository.postgres.CartJpaEntity;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.shared.infrastructure.observability.ActorKeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationalCartServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-16T20:00:00Z");
    private static final UUID CONVERSATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartCatalogReader catalogReader;
    @Mock
    private CatalogConversationService catalogConversationService;
    @Mock
    private CartCheckoutCreator checkoutCreator;
    @Mock
    private ActorKeyGenerator actorKeyGenerator;

    private ConversationalCartService service;

    @BeforeEach
    void setUp() {
        service = new ConversationalCartService(
                cartRepository,
                catalogReader,
                catalogConversationService,
                checkoutCreator,
                actorKeyGenerator,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(actorKeyGenerator.generate(Channel.TELEGRAM, "customer-1"))
                .thenReturn(Optional.of("actor-hash"));
    }

    @Test
    void addsMultipleItemsAndRendersQuantitySubtotalsAndTotal() {
        CartJpaEntity[] savedCart = new CartJpaEntity[1];
        when(cartRepository.findByConversationId(CONVERSATION_ID))
                .thenAnswer(invocation -> Optional.ofNullable(savedCart[0]));
        when(cartRepository.saveAndFlush(any(CartJpaEntity.class)))
                .thenAnswer(invocation -> {
                    savedCart[0] = invocation.getArgument(0);
                    return savedCart[0];
                });

        CatalogFact shirt = fact("Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", "18900.00", 12);
        CatalogFact hoodie = fact("Buzo Spring Boot", "RP-BUZ-SB-NEG-XL", "XL", "Negro", "42900.00", 3);
        stubCatalogSearch(shirt, hoodie);
        when(catalogReader.findBySku(shirt.sku())).thenReturn(Optional.of(toCatalogItem(shirt)));
        when(catalogReader.findBySku(hoodie.sku())).thenReturn(Optional.of(toCatalogItem(hoodie)));

        String first = service.handle(context(
                "Agregá dos remeras NullPointer negras talle M al carrito")).orElseThrow().text();
        String second = service.handle(context(
                "Sumá 1 buzo Spring Boot negro talle XL al carrito")).orElseThrow().text();

        assertTrue(first.contains("2 x Remera NullPointer"));
        assertTrue(second.contains("1 x Buzo Spring Boot"));
        assertTrue(second.contains("Total: 80700.00 ARS"));
        assertEquals(3, savedCart[0].getItems().stream()
                .mapToInt(item -> item.getQuantity()).sum());
        assertEquals(CartStatus.ACTIVE, savedCart[0].getStatus());
    }

    @Test
    void doesNotAddAStockAlternativeWhenTheRequestedVariantDoesNotExist() {
        CatalogFact alternative = fact("Buzo Spring Boot", "RP-BUZ-SB-NEG-XL", "XL", "Negro", "42900.00", 3);
        CatalogSearchResult alternatives = new CatalogSearchResult(
                CatalogSearchResult.Status.ALTERNATIVES,
                List.of(alternative),
                "buzo",
                CatalogSearchResult.FollowUpKind.NONE,
                "RELAXED_FILTERS");
        when(catalogConversationService.searchExact(any(CatalogQuery.class))).thenReturn(Optional.of(alternatives));
        when(catalogConversationService.search(any(CatalogQuery.class), any(), anyString()))
                .thenReturn(Optional.of(alternatives));

        String response = service.handle(context("Agrega al carrito un buzo Spring Boot negro talle M"))
                .orElseThrow().text();

        assertTrue(response.contains("Como alternativa"));
        verify(cartRepository, org.mockito.Mockito.never()).saveAndFlush(any(CartJpaEntity.class));
        verify(checkoutCreator, org.mockito.Mockito.never()).create(any());
    }

    @Test
    void createsOneCheckoutForTheWholeCartAndMarksItPending() {
        CartJpaEntity cart = cart();
        cart.addOrIncrement("RP-REM-NP-NEG-M", 2, NOW);
        cart.addOrIncrement("RP-BUZ-SB-NEG-XL", 1, NOW);
        when(cartRepository.findByConversationId(CONVERSATION_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.saveAndFlush(any(CartJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(checkoutCreator.create(any(CartCheckoutCreator.CreateCartCheckoutRequest.class)))
                .thenReturn(Optional.of(new CartCheckoutCreator.CartCheckout(
                        UUID.fromString("30000000-0000-0000-0000-000000000001"),
                        new BigDecimal("80700.00"), "ARS", "mock", "https://pay.invalid/cart-1")));

        String response = service.handle(context("Confirmar compra")).orElseThrow().text();

        assertTrue(response.contains("80700.00 ARS"));
        assertTrue(response.contains("https://pay.invalid/cart-1"));
        assertEquals(CartStatus.CHECKOUT_PENDING, cart.getStatus());
        verify(checkoutCreator).create(eq(new CartCheckoutCreator.CreateCartCheckoutRequest(
                CONVERSATION_ID,
                "telegram:actor-hash",
                cart.getId(),
                cart.getVersion(),
                List.of(
                        new CartCheckoutCreator.Item("RP-REM-NP-NEG-M", 2),
                        new CartCheckoutCreator.Item("RP-BUZ-SB-NEG-XL", 1)))));
    }

    @Test
    void reviewsTheWholeCartBeforeCreatingTheCheckout() {
        CartJpaEntity cart = cart();
        cart.addOrIncrement("RP-REM-NP-NEG-M", 2, NOW);
        cart.addOrIncrement("RP-BUZ-SB-NEG-XL", 1, NOW);
        when(cartRepository.findByConversationId(CONVERSATION_ID)).thenReturn(Optional.of(cart));
        when(catalogReader.findBySku("RP-REM-NP-NEG-M"))
                .thenReturn(Optional.of(toCatalogItem(fact(
                        "Remera NullPointer", "RP-REM-NP-NEG-M", "M", "Negro", "18900.00", 12))));
        when(catalogReader.findBySku("RP-BUZ-SB-NEG-XL"))
                .thenReturn(Optional.of(toCatalogItem(fact(
                        "Buzo Spring Boot", "RP-BUZ-SB-NEG-XL", "XL", "Negro", "42900.00", 3))));

        String response = service.handle(context("Quiero pagar")).orElseThrow().text();

        assertTrue(response.contains("2 x Remera NullPointer"));
        assertTrue(response.contains("1 x Buzo Spring Boot"));
        assertTrue(response.contains("¿Confirmás la compra?"));
        verify(checkoutCreator, org.mockito.Mockito.never()).create(any());
    }

    @Test
    void resetsTheCartAndCancelsItsPendingCheckoutWhenConversationRestarts() {
        CartJpaEntity cart = cart();
        cart.addOrIncrement("RP-REM-NP-NEG-M", 1, NOW);
        cart.markCheckoutPending(UUID.randomUUID(), NOW);
        when(cartRepository.findByConversationId(CONVERSATION_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.saveAndFlush(any(CartJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.reset(context("start"));

        assertTrue(cart.getItems().isEmpty());
        assertEquals(CartStatus.ACTIVE, cart.getStatus());
        verify(checkoutCreator).cancelActive(cart.getId());
        verify(cartRepository).saveAndFlush(cart);
    }

    @Test
    void doesNotCreateASecondCheckoutWhileThePreviousLinkIsActive() {
        CartJpaEntity cart = cart();
        cart.addOrIncrement("RP-REM-NP-NEG-M", 1, NOW);
        cart.markCheckoutPending(UUID.randomUUID(), NOW);
        when(cartRepository.findByConversationId(CONVERSATION_ID)).thenReturn(Optional.of(cart));

        String response = service.handle(context("Confirmar compra")).orElseThrow().text();

        assertTrue(response.contains("ya tiene un link de pago activo"));
        verify(checkoutCreator, org.mockito.Mockito.never()).create(any());
    }

    @Test
    void cancelsCheckoutAndReopensTheSameCartForModification() {
        CartJpaEntity cart = cart();
        cart.addOrIncrement("RP-REM-NP-NEG-M", 1, NOW);
        cart.markCheckoutPending(UUID.randomUUID(), NOW);
        when(cartRepository.findByConversationId(CONVERSATION_ID)).thenReturn(Optional.of(cart));
        when(cartRepository.saveAndFlush(any(CartJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String response = service.handle(context("Cancelar compra")).orElseThrow().text();

        assertEquals(CartStatus.ACTIVE, cart.getStatus());
        assertTrue(response.contains("cancelé el checkout anterior"));
        verify(checkoutCreator).cancelActive(cart.getId());
    }

    private void stubCatalogSearch(CatalogFact shirt, CatalogFact hoodie) {
        when(catalogConversationService.searchExact(any(CatalogQuery.class)))
                .thenAnswer(invocation -> {
                    CatalogQuery query = invocation.getArgument(0);
                    CatalogFact fact = "buzo".equals(query.productType()) ? hoodie : shirt;
                    return Optional.of(new CatalogSearchResult(
                            CatalogSearchResult.Status.MATCHED,
                            List.of(fact),
                            null,
                            CatalogSearchResult.FollowUpKind.NONE,
                            "MATCHED"));
                });
    }

    private CartJpaEntity cart() {
        return new CartJpaEntity(
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                CONVERSATION_ID,
                "actor-hash",
                Channel.TELEGRAM,
                "ARS",
                NOW);
    }

    private ConversationContext context(String message) {
        return new ConversationContext(
                CONVERSATION_ID,
                "customer-1",
                message,
                List.of(message),
                List.of(),
                null,
                List.of(),
                Channel.TELEGRAM);
    }

    private static CatalogFact fact(
            String productName,
            String sku,
            String size,
            String color,
            String price,
            int stock) {
        return new CatalogFact(productName, sku, size, color, new BigDecimal(price), "ARS", stock);
    }

    private static CartCatalogReader.CatalogItem toCatalogItem(CatalogFact fact) {
        return new CartCatalogReader.CatalogItem(
                fact.sku(), fact.productName(), fact.size(), fact.color(), fact.price(), fact.currency(),
                fact.stock(), true);
    }
}
