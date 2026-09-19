package com.wally.customersupport.conversation.application.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.wally.customersupport.cart.application.port.in.CartConversationHandler;
import com.wally.customersupport.cart.application.port.out.CartRepository;
import com.wally.customersupport.cart.application.service.CartCommandParser;
import com.wally.customersupport.cart.domain.model.CartStatus;
import com.wally.customersupport.cart.infrastructure.repository.postgres.CartJpaEntity;
import com.wally.customersupport.conversation.domain.model.Channel;
import com.wally.customersupport.conversation.domain.model.ConversationContext;
import com.wally.customersupport.shared.infrastructure.observability.ActorKeyGenerator;
import org.junit.jupiter.api.Test;

class CheckoutCreateToolTest {

    private static final Instant NOW = Instant.parse("2026-09-19T01:00:00Z");
    private static final UUID CONVERSATION_ID = UUID.fromString("10000000-0000-0000-0000-000000000012");

    @Test
    void createsCheckoutFromTheExpectedOwnedCartVersion() {
        CartConversationHandler handler = mock(CartConversationHandler.class);
        CartRepository repository = mock(CartRepository.class);
        ActorKeyGenerator actorKeys = mock(ActorKeyGenerator.class);
        CartJpaEntity cart = cart();
        cart.addOrIncrement("RP-REM-NP-NEG-M", 1, NOW);
        ConversationContext context = context();
        when(actorKeys.generate(Channel.TELEGRAM, "customer-1"))
                .thenReturn(Optional.of("actor-hash"));
        when(repository.findByConversationId(CONVERSATION_ID)).thenReturn(Optional.of(cart));
        when(handler.handle(context, new CartCommandParser.Command(
                CartCommandParser.Action.CONFIRM, null, 1)))
                .thenAnswer(invocation -> {
                    cart.markCheckoutPending(UUID.randomUUID(), NOW);
                    return Optional.of(new CartConversationHandler.Response(
                            "Listo: https://pay.invalid/checkout-1"));
                });

        CheckoutCreateTool.Result result = new CheckoutCreateTool(handler, repository, actorKeys)
                .execute(new CheckoutCreateTool.Input(context, true, cart.getVersion()));

        assertThat(result.status()).isEqualTo(CheckoutCreateTool.Status.CREATED);
        assertThat(result.orderCreated()).isTrue();
        assertThat(result.paymentLinkCreated()).isTrue();
    }

    @Test
    void reusesAnActiveCheckoutInsteadOfCreatingAnotherLink() {
        CartConversationHandler handler = mock(CartConversationHandler.class);
        CartRepository repository = mock(CartRepository.class);
        ActorKeyGenerator actorKeys = mock(ActorKeyGenerator.class);
        CartJpaEntity cart = cart();
        cart.addOrIncrement("SKU", 1, NOW);
        cart.markCheckoutPending(UUID.randomUUID(), NOW);
        ConversationContext context = context();
        when(actorKeys.generate(Channel.TELEGRAM, "customer-1"))
                .thenReturn(Optional.of("actor-hash"));
        when(repository.findByConversationId(CONVERSATION_ID)).thenReturn(Optional.of(cart));

        CheckoutCreateTool.Result result = new CheckoutCreateTool(handler, repository, actorKeys)
                .execute(new CheckoutCreateTool.Input(context, true, cart.getVersion()));

        assertThat(result.status()).isEqualTo(CheckoutCreateTool.Status.REUSED);
        assertThat(result.paymentLinkCreated()).isTrue();
        org.mockito.Mockito.verifyNoInteractions(handler);
    }

    private static CartJpaEntity cart() {
        return new CartJpaEntity(
                UUID.randomUUID(), CONVERSATION_ID, "actor-hash", Channel.TELEGRAM, "ARS", NOW);
    }

    private static ConversationContext context() {
        return new ConversationContext(
                CONVERSATION_ID, "customer-1", "confirmar compra", List.of("confirmar compra"), List.of(),
                null, List.of(), Channel.TELEGRAM);
    }
}
