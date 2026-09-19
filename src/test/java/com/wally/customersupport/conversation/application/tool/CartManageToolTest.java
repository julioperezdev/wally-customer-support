package com.wally.customersupport.conversation.application.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.cart.application.port.in.CartConversationHandler;
import com.wally.customersupport.cart.application.port.out.CartCatalogReader;
import com.wally.customersupport.cart.application.port.out.CartRepository;
import com.wally.customersupport.cart.infrastructure.repository.postgres.CartJpaEntity;
import com.wally.customersupport.catalog.domain.model.CatalogQuery;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.shared.infrastructure.observability.ActorKeyGenerator;
import org.junit.jupiter.api.Test;

class CartManageToolTest {

    private static final Instant NOW = Instant.parse("2026-09-19T01:00:00Z");
    private static final UUID CONVERSATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000011");

    @Test
    void exposesAValidatedCartSnapshotAfterAHandlerOperation() {
        CartConversationHandler handler = mock(CartConversationHandler.class);
        CartRepository repository = mock(CartRepository.class);
        CartCatalogReader catalog = mock(CartCatalogReader.class);
        ActorKeyGenerator actorKeys = mock(ActorKeyGenerator.class);
        CartJpaEntity cart = new CartJpaEntity(
                UUID.randomUUID(), CONVERSATION_ID, "actor-hash", Channel.TELEGRAM, "ARS", NOW);
        cart.addOrIncrement("RP-REM-NP-NEG-M", 2, NOW);
        ConversationContext context = context("Ver carrito", "customer-1");

        when(actorKeys.generate(Channel.TELEGRAM, "customer-1"))
                .thenReturn(Optional.of("actor-hash"));
        when(handler.handle(context, new com.wally.customersupport.cart.application.service.CartCommandParser.Command(
                com.wally.customersupport.cart.application.service.CartCommandParser.Action.VIEW,
                CatalogQuery.empty(), 1)))
                .thenReturn(Optional.of(new CartConversationHandler.Response("Tu carrito tiene 2 unidades.")));
        when(repository.findByConversationId(CONVERSATION_ID)).thenReturn(Optional.of(cart));
        when(catalog.findBySku("RP-REM-NP-NEG-M")).thenReturn(Optional.of(
                new CartCatalogReader.CatalogItem(
                        "RP-REM-NP-NEG-M", "Remera NullPointer", "M", "Negro",
                        new BigDecimal("18900"), "ARS", 12, true)));

        CartManageTool.Result result = new CartManageTool(handler, repository, catalog, actorKeys)
                .execute(new CartManageTool.Input(context, CartManageTool.Operation.VIEW, null, 1));

        assertThat(result.status()).isEqualTo(CartManageTool.Status.VIEWED);
        assertThat(result.itemCount()).isEqualTo(2);
        assertThat(result.total()).isEqualByComparingTo("37800");
        assertThat(result.currency()).isEqualTo("ARS");
    }

    @Test
    void doesNotExposeAnotherActorsCartInTheSnapshot() {
        CartConversationHandler handler = mock(CartConversationHandler.class);
        CartRepository repository = mock(CartRepository.class);
        CartCatalogReader catalog = mock(CartCatalogReader.class);
        ActorKeyGenerator actorKeys = mock(ActorKeyGenerator.class);
        CartJpaEntity cart = new CartJpaEntity(
                UUID.randomUUID(), CONVERSATION_ID, "other-actor", Channel.TELEGRAM, "ARS", NOW);
        cart.addOrIncrement("SKU", 1, NOW);
        ConversationContext context = context("Ver carrito", "customer-1");
        when(actorKeys.generate(Channel.TELEGRAM, "customer-1"))
                .thenReturn(Optional.of("actor-hash"));
        when(handler.handle(context, new com.wally.customersupport.cart.application.service.CartCommandParser.Command(
                com.wally.customersupport.cart.application.service.CartCommandParser.Action.VIEW,
                CatalogQuery.empty(), 1)))
                .thenReturn(Optional.of(new CartConversationHandler.Response("No puedo mostrar ese carrito.")));
        when(repository.findByConversationId(CONVERSATION_ID)).thenReturn(Optional.of(cart));

        CartManageTool.Result result = new CartManageTool(handler, repository, catalog, actorKeys)
                .execute(new CartManageTool.Input(context, CartManageTool.Operation.VIEW, null, 1));

        assertThat(result.itemCount()).isZero();
        assertThat(result.total()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.currency()).isNull();
    }

    private static ConversationContext context(String message, String customerId) {
        return new ConversationContext(
                CONVERSATION_ID, customerId, message, List.of(message), List.of(),
                null, List.of(), Channel.TELEGRAM);
    }
}
